#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace player2::dsp {

/**
 * Lock-free fixed-capacity WSOLA time-scale and pitch-shift processor running at the
 * device output boundary.
 *
 * When tempoRatio == 1.0 and pitchSemitones == 0.0 callers bypass this stage completely
 * so normal playback keeps bit-exact sample alignment.
 *
 * When active, pitchRatio = 2^(pitchSemitones/12) controls the intra-grain resampling step
 * while tempoRatio advances the nominal grain origin in transport time, keeping clock sync
 * aligned to tempoRatio with similarity-based overlap-add to eliminate phase artifacts.
 */
class TimePitchShifter {
public:
    static constexpr std::size_t kWin = 2048;
    static constexpr std::size_t kHop = kWin / 2; // 50% overlap -> periodic Hann sums to 1.0
    static constexpr std::size_t kInCap = 32768;
    static constexpr std::size_t kInMask = kInCap - 1;
    static constexpr std::int64_t kSearchRadius = 384;

private:
    std::array<float, kInCap * 2> inBuf_{};
    std::array<float, kHop * 2> prevTail_{};
    std::array<float, kHop * 2> readyOut_{};
    std::array<float, kWin> hann_{};

    std::int64_t totalIn_{0};
    double nominalCursor_{0.0};
    double prevAlignedEnd_{0.0};
    bool hasPrev_{false};
    std::size_t readyReadPos_{kHop};
    bool hannInit_{false};

    void ensureHann() noexcept {
        if (hannInit_) return;
        constexpr double kTwoPi = 6.28318530717958647692;
        for (std::size_t i = 0; i < kWin; ++i) {
            hann_[i] = static_cast<float>(0.5 * (1.0 - std::cos(kTwoPi * static_cast<double>(i) / static_cast<double>(kWin))));
        }
        hannInit_ = true;
    }

    inline void sampleLinear(double pos, float& outL, float& outR) const noexcept {
        if (pos <= 0.0) {
            outL = inBuf_[0];
            outR = inBuf_[1];
            return;
        }
        const auto maxValid = totalIn_ > 0 ? totalIn_ - 1 : 0;
        const auto idx0 = std::min<std::int64_t>(static_cast<std::int64_t>(pos), maxValid);
        const auto idx1 = std::min<std::int64_t>(idx0 + 1, maxValid);
        const float frac = static_cast<float>(pos - static_cast<double>(idx0));
        const std::size_t s0 = static_cast<std::size_t>(idx0 & static_cast<std::int64_t>(kInMask)) * 2;
        const std::size_t s1 = static_cast<std::size_t>(idx1 & static_cast<std::int64_t>(kInMask)) * 2;
        outL = inBuf_[s0] + (inBuf_[s1] - inBuf_[s0]) * frac;
        outR = inBuf_[s0 + 1] + (inBuf_[s1 + 1] - inBuf_[s0 + 1]) * frac;
    }

    std::int64_t findBestOffset(double anchor, double baseCursor, double srcStep) const noexcept {
        const std::int64_t minSafe = std::max<std::int64_t>(0, totalIn_ - static_cast<std::int64_t>(kInCap) + 64);
        const std::int64_t candCenter = static_cast<std::int64_t>(std::llround(baseCursor));
        const std::int64_t spanNeeded = static_cast<std::int64_t>(std::ceil(static_cast<double>(kWin) * srcStep)) + 4;
        const std::int64_t maxCand = totalIn_ - spanNeeded;
        const std::int64_t low = std::max(minSafe, candCenter - kSearchRadius);
        const std::int64_t high = std::min(maxCand, candCenter + kSearchRadius);
        if (low >= high) return std::clamp(candCenter, minSafe, std::max(minSafe, maxCand));

        const std::int64_t anchorInt = static_cast<std::int64_t>(std::llround(anchor));
        if (anchorInt < minSafe || anchorInt + spanNeeded > totalIn_) {
            return std::clamp(candCenter, minSafe, std::max(minSafe, maxCand));
        }

        const std::int64_t cmpLen = std::min<std::int64_t>(kHop, 512);
        const std::int64_t stride = 8;

        auto scoreAt = [&](std::int64_t c) noexcept -> double {
            double dot = 0.0;
            double eA = 1e-9;
            double eC = 1e-9;
            for (std::int64_t k = 0; k < cmpLen; k += stride) {
                const double srcOff = static_cast<double>(k) * srcStep;
                float al = 0.0f, ar = 0.0f, cl = 0.0f, cr = 0.0f;
                sampleLinear(static_cast<double>(anchorInt) + srcOff, al, ar);
                sampleLinear(static_cast<double>(c) + srcOff, cl, cr);
                const float am = 0.5f * (al + ar);
                const float cm = 0.5f * (cl + cr);
                dot += static_cast<double>(am) * static_cast<double>(cm);
                eA += static_cast<double>(am) * static_cast<double>(am);
                eC += static_cast<double>(cm) * static_cast<double>(cm);
            }
            return dot / std::sqrt(eA * eC);
        };

        std::int64_t bestCoarse = candCenter;
        double bestScore = -2.0;
        for (std::int64_t c = low; c <= high; c += 4) {
            const double s = scoreAt(c);
            if (s > bestScore) {
                bestScore = s;
                bestCoarse = c;
            }
        }
        std::int64_t best = bestCoarse;
        for (std::int64_t c = std::max(low, bestCoarse - 3); c <= std::min(high, bestCoarse + 3); ++c) {
            const double s = scoreAt(c);
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

public:
    void reset() noexcept {
        inBuf_.fill(0.0f);
        prevTail_.fill(0.0f);
        readyOut_.fill(0.0f);
        totalIn_ = 0;
        nominalCursor_ = 0.0;
        prevAlignedEnd_ = 0.0;
        hasPrev_ = false;
        readyReadPos_ = kHop;
    }

    template <typename PullFn>
    void render(
        float* output,
        std::size_t frames,
        int transportRate,
        int deviceRate,
        double tempoRatio,
        double pitchSemitones,
        PullFn&& pullTransport
    ) noexcept {
        if (!output || frames == 0 || transportRate <= 0 || deviceRate <= 0) return;
        ensureHann();

        const double clampedTempo = std::clamp(tempoRatio, 0.5, 2.0);
        const double clampedPitch = std::clamp(pitchSemitones, -12.0, 12.0);
        const double pitchRatio = std::pow(2.0, clampedPitch / 12.0);
        const double srcStep = pitchRatio * static_cast<double>(transportRate) / static_cast<double>(deviceRate);
        const double hopIn = static_cast<double>(kHop) * clampedTempo * static_cast<double>(transportRate) / static_cast<double>(deviceRate);
        const std::int64_t maxSpan = static_cast<std::int64_t>(std::ceil(static_cast<double>(kWin) * srcStep)) + 8;
        const std::int64_t effectiveRadius = std::min<std::int64_t>(kSearchRadius, std::max<std::int64_t>(0, (static_cast<std::int64_t>(kInCap) / 4) - maxSpan));

        std::array<float, 1024> chunk{};
        std::size_t outPos = 0;

        while (outPos < frames) {
            if (readyReadPos_ < kHop) {
                const std::size_t avail = kHop - readyReadPos_;
                const std::size_t take = std::min(avail, frames - outPos);
                for (std::size_t i = 0; i < take; ++i) {
                    const std::size_t src = (readyReadPos_ + i) * 2;
                    const std::size_t dst = (outPos + i) * 2;
                    output[dst] = readyOut_[src];
                    output[dst + 1] = readyOut_[src + 1];
                }
                readyReadPos_ += take;
                outPos += take;
                continue;
            }

            // Pull enough future transport frames for current grain + similarity lookahead.
            const std::int64_t needEnd = static_cast<std::int64_t>(std::ceil(nominalCursor_)) + maxSpan + effectiveRadius + 4;
            while (totalIn_ < needEnd) {
                const std::size_t want = std::min<std::size_t>(512, static_cast<std::size_t>(needEnd - totalIn_));
                pullTransport(chunk.data(), want);
                for (std::size_t k = 0; k < want; ++k) {
                    const std::size_t wIdx = static_cast<std::size_t>(totalIn_ & static_cast<std::int64_t>(kInMask)) * 2;
                    inBuf_[wIdx] = chunk[k * 2];
                    inBuf_[wIdx + 1] = chunk[k * 2 + 1];
                    ++totalIn_;
                }
            }

            const std::int64_t alignedStart = hasPrev_
                ? findBestOffset(prevAlignedEnd_, nominalCursor_, srcStep)
                : static_cast<std::int64_t>(std::llround(nominalCursor_));

            if (!hasPrev_) {
                for (std::size_t n = 0; n < kHop; ++n) {
                    float sl = 0.0f, sr = 0.0f;
                    sampleLinear(static_cast<double>(alignedStart) + static_cast<double>(n) * srcStep, sl, sr);
                    const float ramp = n < 128 ? static_cast<float>(n) / 128.0f : 1.0f;
                    readyOut_[n * 2] = sl * ramp;
                    readyOut_[n * 2 + 1] = sr * ramp;
                }
                for (std::size_t n = 0; n < kHop; ++n) {
                    float sl = 0.0f, sr = 0.0f;
                    sampleLinear(static_cast<double>(alignedStart) + static_cast<double>(n + kHop) * srcStep, sl, sr);
                    const float w = hann_[n + kHop];
                    prevTail_[n * 2] = sl * w;
                    prevTail_[n * 2 + 1] = sr * w;
                }
                hasPrev_ = true;
            } else {
                for (std::size_t n = 0; n < kHop; ++n) {
                    float sl = 0.0f, sr = 0.0f;
                    sampleLinear(static_cast<double>(alignedStart) + static_cast<double>(n) * srcStep, sl, sr);
                    const float wHead = hann_[n];
                    readyOut_[n * 2] = prevTail_[n * 2] + sl * wHead;
                    readyOut_[n * 2 + 1] = prevTail_[n * 2 + 1] + sr * wHead;
                }
                for (std::size_t n = 0; n < kHop; ++n) {
                    float sl = 0.0f, sr = 0.0f;
                    sampleLinear(static_cast<double>(alignedStart) + static_cast<double>(n + kHop) * srcStep, sl, sr);
                    const float wTail = hann_[n + kHop];
                    prevTail_[n * 2] = sl * wTail;
                    prevTail_[n * 2 + 1] = sr * wTail;
                }
            }

            prevAlignedEnd_ = static_cast<double>(alignedStart) + static_cast<double>(kHop) * srcStep;
            nominalCursor_ += hopIn;
            readyReadPos_ = 0;
        }
    }
};

} // namespace player2::dsp
