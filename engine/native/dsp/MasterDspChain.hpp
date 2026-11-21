#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace player2::dsp {

struct MasterDspConfig {
    bool eqEnabled{false};
    std::array<double, 5> bandDb{0.0, 0.0, 0.0, 0.0, 0.0};
    double bassBoostDb{0.0};   // 0..9 dB
    double loudnessDb{0.0};    // 0..9 dB
};

class StereoBiquad {
    double b0_{1.0}, b1_{0.0}, b2_{0.0}, a1_{0.0}, a2_{0.0};
    std::array<double, 2> z1_{0.0, 0.0};
    std::array<double, 2> z2_{0.0, 0.0};
    bool bypass_{true};

public:
    enum class Kind { LowShelf, Peak, HighShelf };

    void reset() noexcept {
        z1_.fill(0.0);
        z2_.fill(0.0);
    }

    void configure(Kind kind, double rate, double freq, double param, double gainDb) noexcept {
        if (rate < 8000.0 || std::abs(gainDb) < 0.05) {
            bypass_ = true;
            b0_ = 1.0; b1_ = 0.0; b2_ = 0.0; a1_ = 0.0; a2_ = 0.0;
            return;
        }
        constexpr double kPi = 3.14159265358979323846;
        const double clampedFreq = std::clamp(freq, 20.0, rate * 0.45);
        const double clampedGain = std::clamp(gainDb, -15.0, 15.0);
        const double a = std::pow(10.0, clampedGain / 40.0);
        const double w0 = 2.0 * kPi * clampedFreq / rate;
        const double cosW = std::cos(w0);
        const double sinW = std::sin(w0);

        double a0 = 1.0;
        double nb0 = 1.0, nb1 = 0.0, nb2 = 0.0, na1 = 0.0, na2 = 0.0;

        if (kind == Kind::Peak) {
            const double q = std::clamp(param, 0.2, 10.0);
            const double alpha = sinW / (2.0 * q);
            nb0 = 1.0 + alpha * a;
            nb1 = -2.0 * cosW;
            nb2 = 1.0 - alpha * a;
            a0 = 1.0 + alpha / a;
            na1 = -2.0 * cosW;
            na2 = 1.0 - alpha / a;
        } else {
            const double slope = std::clamp(param, 0.25, 2.0);
            const double inside = std::max(0.0, (a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0);
            const double alpha = (sinW / 2.0) * std::sqrt(inside);
            const double twoSqrtAAlpha = 2.0 * std::sqrt(a) * alpha;
            if (kind == Kind::LowShelf) {
                nb0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha);
                nb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW);
                nb2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha);
                a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha;
                na1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW);
                na2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha;
            } else {
                nb0 = a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha);
                nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW);
                nb2 = a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha);
                a0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha;
                na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW);
                na2 = (a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha;
            }
        }
        if (std::abs(a0) < 1e-12) {
            bypass_ = true;
            return;
        }
        b0_ = nb0 / a0;
        b1_ = nb1 / a0;
        b2_ = nb2 / a0;
        a1_ = na1 / a0;
        a2_ = na2 / a0;
        bypass_ = false;
    }

    inline float process(float x, int ch) noexcept {
        if (bypass_) return x;
        const double in = static_cast<double>(x);
        const double y = b0_ * in + z1_[ch];
        z1_[ch] = b1_ * in - a1_ * y + z2_[ch];
        z2_[ch] = b2_ * in - a2_ * y;
        if (!std::isfinite(y)) {
            z1_[ch] = 0.0;
            z2_[ch] = 0.0;
            return 0.0f;
        }
        return static_cast<float>(y);
    }

    bool isBypassed() const noexcept { return bypass_; }
};

class MasterDspMailbox {
    std::atomic<std::uint64_t> seq_{0};
    std::atomic<int> eqEnabled_{0};
    std::array<std::atomic<int>, 5> bandsMilliDb_{};
    std::atomic<int> bassBoostMilliDb_{0};
    std::atomic<int> loudnessMilliDb_{0};

public:
    void write(const MasterDspConfig& cfg) noexcept {
        eqEnabled_.store(cfg.eqEnabled ? 1 : 0, std::memory_order_relaxed);
        for (std::size_t i = 0; i < 5; ++i) {
            const int m = static_cast<int>(std::llround(std::clamp(cfg.bandDb[i], -12.0, 12.0) * 1000.0));
            bandsMilliDb_[i].store(m, std::memory_order_relaxed);
        }
        bassBoostMilliDb_.store(
            static_cast<int>(std::llround(std::clamp(cfg.bassBoostDb, 0.0, 9.0) * 1000.0)),
            std::memory_order_relaxed
        );
        loudnessMilliDb_.store(
            static_cast<int>(std::llround(std::clamp(cfg.loudnessDb, 0.0, 9.0) * 1000.0)),
            std::memory_order_relaxed
        );
        seq_.fetch_add(1, std::memory_order_release);
    }

    std::uint64_t version() const noexcept {
        return seq_.load(std::memory_order_acquire);
    }

    MasterDspConfig read() const noexcept {
        MasterDspConfig cfg;
        cfg.eqEnabled = eqEnabled_.load(std::memory_order_relaxed) != 0;
        for (std::size_t i = 0; i < 5; ++i) {
            cfg.bandDb[i] = static_cast<double>(bandsMilliDb_[i].load(std::memory_order_relaxed)) / 1000.0;
        }
        cfg.bassBoostDb = static_cast<double>(bassBoostMilliDb_.load(std::memory_order_relaxed)) / 1000.0;
        cfg.loudnessDb = static_cast<double>(loudnessMilliDb_.load(std::memory_order_relaxed)) / 1000.0;
        return cfg;
    }
};

class MasterDspProcessor {
    std::array<StereoBiquad, 5> eqBands_{};
    StereoBiquad bassFilter_{};
    std::uint64_t appliedSeq_{UINT64_MAX};
    int appliedRate_{0};
    MasterDspConfig activeCfg_{};
    double envPower_{1e-6};
    double currentLoudnessGain_{1.0};
    bool anyActive_{false};

    void updateIfNeeded(const MasterDspMailbox& mailbox, int rate) noexcept {
        const auto ver = mailbox.version();
        if (ver == appliedSeq_ && rate == appliedRate_) return;
        activeCfg_ = mailbox.read();
        appliedSeq_ = ver;
        appliedRate_ = rate;

        constexpr std::array<double, 5> kFreqs{60.0, 230.0, 910.0, 3600.0, 12000.0};
        constexpr std::array<StereoBiquad::Kind, 5> kKinds{
            StereoBiquad::Kind::LowShelf,
            StereoBiquad::Kind::Peak,
            StereoBiquad::Kind::Peak,
            StereoBiquad::Kind::Peak,
            StereoBiquad::Kind::HighShelf,
        };
        constexpr std::array<double, 5> kParams{1.0, 1.0, 1.0, 1.0, 1.0};

        bool active = false;
        for (std::size_t i = 0; i < 5; ++i) {
            const double gain = activeCfg_.eqEnabled ? activeCfg_.bandDb[i] : 0.0;
            eqBands_[i].configure(kKinds[i], static_cast<double>(rate), kFreqs[i], kParams[i], gain);
            if (!eqBands_[i].isBypassed()) active = true;
        }
        bassFilter_.configure(
            StereoBiquad::Kind::LowShelf,
            static_cast<double>(rate),
            85.0,
            0.9,
            activeCfg_.bassBoostDb
        );
        if (!bassFilter_.isBypassed()) active = true;
        if (activeCfg_.loudnessDb >= 0.05) active = true;
        anyActive_ = active;
    }

public:
    void reset() noexcept {
        for (auto& b : eqBands_) b.reset();
        bassFilter_.reset();
        envPower_ = 1e-6;
        currentLoudnessGain_ = 1.0;
    }

    void process(float* stereo, std::size_t frames, int rate, const MasterDspMailbox& mailbox) noexcept {
        if (!stereo || frames == 0 || rate <= 0) return;
        updateIfNeeded(mailbox, rate);
        if (!anyActive_) return;

        const bool useLoudness = activeCfg_.loudnessDb >= 0.05;
        const double maxBoostLin = useLoudness ? std::pow(10.0, activeCfg_.loudnessDb / 20.0) : 1.0;
        // Smooth RMS tracker (~80ms attack/release at 48 kHz)
        const double alphaEnv = 1.0 - std::exp(-1.0 / std::max(1.0, 0.08 * static_cast<double>(rate)));
        const double alphaGain = 1.0 - std::exp(-1.0 / std::max(1.0, 0.025 * static_cast<double>(rate)));

        for (std::size_t n = 0; n < frames; ++n) {
            float l = stereo[n * 2];
            float r = stereo[n * 2 + 1];

            for (auto& band : eqBands_) {
                if (!band.isBypassed()) {
                    l = band.process(l, 0);
                    r = band.process(r, 1);
                }
            }
            if (!bassFilter_.isBypassed()) {
                l = bassFilter_.process(l, 0);
                r = bassFilter_.process(r, 1);
            }

            if (useLoudness) {
                const double instPower = 0.5 * (static_cast<double>(l) * l + static_cast<double>(r) * r);
                envPower_ += alphaEnv * (instPower - envPower_);
                const double rms = std::sqrt(std::max(envPower_, 1e-9));
                // Target comfortable normalized RMS ~0.22 (-13 dBFS) capped by maxBoostLin
                double desired = std::clamp(0.22 / rms, 0.5, maxBoostLin);
                const double peak = std::max(std::abs(l), std::abs(r));
                if (peak * desired > 0.92) {
                    desired = std::min(desired, 0.92 / std::max(static_cast<double>(peak), 1e-6));
                }
                currentLoudnessGain_ += alphaGain * (desired - currentLoudnessGain_);
                l = static_cast<float>(static_cast<double>(l) * currentLoudnessGain_);
                r = static_cast<float>(static_cast<double>(r) * currentLoudnessGain_);
            }

            // Gentle soft limiter above 0.94 to avoid harsh clipping from boosted bands
            auto softLimit = [](float x) noexcept -> float {
                if (!std::isfinite(x)) return 0.0f;
                const float ax = std::abs(x);
                if (ax <= 0.94f) return x;
                const float sign = x < 0.0f ? -1.0f : 1.0f;
                const float excess = (ax - 0.94f) / 0.06f;
                const float shaped = 0.94f + 0.055f * std::tanh(excess);
                return sign * std::min(shaped, 0.995f);
            };
            stereo[n * 2] = softLimit(l);
            stereo[n * 2 + 1] = softLimit(r);
        }
    }
};

} // namespace player2::dsp
