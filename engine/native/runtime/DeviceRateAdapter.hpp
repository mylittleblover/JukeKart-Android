#pragma once
#include "runtime/EngineRuntime.hpp"
#include "dsp/TimePitchShifter.hpp"
namespace player2 {
// Engine owns rate conversion and coordinate carry; Platform only supplies device rate/frames.
class DeviceRateAdapter {
    Stereo current_{}, next_{};
    std::uint64_t phase_{};
    int deviceRate_{};
    bool initialized_{};
    audio::DeviceFrame position_{};
    dsp::TimePitchShifter shifter_{};
    bool wasShifting_{};
public:
    void reset() noexcept {
        current_ = next_ = {};
        phase_ = 0;
        deviceRate_ = 0;
        initialized_ = false;
        position_ = {};
        shifter_.reset();
        wasShifting_ = false;
    }
    void render(
        EngineRuntime& runtime,
        float* output,
        std::size_t frames,
        int rate,
        double tempoRatio = 1.0,
        double pitchSemitones = 0.0
    ) noexcept {
        const int transportRate = runtime.sampleRate();
        if (rate < 8000 || rate > 192000 || transportRate < 8000 || transportRate > 192000) {
            std::fill(output, output + frames * 2, 0.0f);
            return;
        }
        const double clampedTempo = std::clamp(tempoRatio, 0.5, 2.0);
        const double clampedPitch = std::clamp(pitchSemitones, -12.0, 12.0);
        const bool shifting = std::abs(clampedTempo - 1.0) >= 1e-4 || std::abs(clampedPitch) >= 1e-4;

        if (shifting) {
            if (!wasShifting_) {
                shifter_.reset();
                wasShifting_ = true;
            }
            shifter_.render(
                output,
                frames,
                transportRate,
                rate,
                clampedTempo,
                clampedPitch,
                [&runtime](float* dst, std::size_t count) noexcept {
                    runtime.render(dst, count);
                }
            );
            for (std::size_t n = 0; n < frames * 2; ++n) {
                const float s = output[n];
                output[n] = std::isfinite(s) ? std::clamp(s, -1.0f, 1.0f) : 0.0f;
            }
            if (frames <= static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max() - position_.value)) {
                position_.value += frames;
            }
            return;
        }

        if (wasShifting_) {
            current_ = next_ = {};
            phase_ = 0;
            deviceRate_ = 0;
            initialized_ = false;
            wasShifting_ = false;
        }

        if (!initialized_) {
            float first[4]{}; runtime.render(first, 2);
            current_ = {first[0], first[1]}; next_ = {first[2], first[3]}; initialized_ = true;
        }
        if (deviceRate_ && deviceRate_ != rate) phase_ = phase_ * rate / deviceRate_;
        deviceRate_ = rate;
        if (rate == transportRate && phase_ == 0) {
            if (frames == 1) {
                output[0] = std::isfinite(current_.left) ? std::clamp(current_.left, -1.0f, 1.0f) : 0.0f;
                output[1] = std::isfinite(current_.right) ? std::clamp(current_.right, -1.0f, 1.0f) : 0.0f;
                float one[2]{};
                runtime.render(one, 1);
                current_ = next_;
                next_ = {one[0], one[1]};
            } else if (frames >= 2) {
                const Stereo oldCurrent = current_;
                const Stereo oldNext = next_;
                runtime.render(output, frames);
                const Stereo newCurrent{output[(frames - 2) * 2], output[(frames - 2) * 2 + 1]};
                const Stereo newNext{output[(frames - 1) * 2], output[(frames - 1) * 2 + 1]};
                for (std::size_t n = frames - 1; n >= 2; --n) {
                    const float l = output[(n - 2) * 2];
                    const float r = output[(n - 2) * 2 + 1];
                    output[n * 2] = std::isfinite(l) ? std::clamp(l, -1.0f, 1.0f) : 0.0f;
                    output[n * 2 + 1] = std::isfinite(r) ? std::clamp(r, -1.0f, 1.0f) : 0.0f;
                }
                output[0] = std::isfinite(oldCurrent.left) ? std::clamp(oldCurrent.left, -1.0f, 1.0f) : 0.0f;
                output[1] = std::isfinite(oldCurrent.right) ? std::clamp(oldCurrent.right, -1.0f, 1.0f) : 0.0f;
                output[2] = std::isfinite(oldNext.left) ? std::clamp(oldNext.left, -1.0f, 1.0f) : 0.0f;
                output[3] = std::isfinite(oldNext.right) ? std::clamp(oldNext.right, -1.0f, 1.0f) : 0.0f;
                current_ = newCurrent;
                next_ = newNext;
            }
            if (frames <= static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max() - position_.value)) position_.value += frames;
            return;
        }
        renderResampled(runtime, output, frames, rate, transportRate);
        if (frames <= static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max() - position_.value)) position_.value += frames;
    }
    audio::DeviceFrame position() const noexcept { return position_; }
private:
#if defined(__GNUC__) || defined(__clang__)
    __attribute__((noinline))
#endif
    void renderResampled(EngineRuntime& runtime, float* output, std::size_t frames, int rate, int transportRate) noexcept {
        // Render exactly the same transport samples as the scalar adapter, in
        // bounded blocks. No extra read-ahead, allocation or decoder work here.
        // 128 device frames need at most 3,072 transport frames (192 kHz / 8 kHz).
        constexpr std::size_t DeviceBlock = 128, TransportBlock = DeviceBlock * 192000 / 8000;
        std::array<float, TransportBlock * 2> samples;
        for (std::size_t offset = 0; offset < frames;) {
            const auto count = std::min(DeviceBlock, frames - offset);
            const auto needed = static_cast<std::size_t>((phase_ + count * static_cast<std::uint64_t>(transportRate)) / rate);
            if (needed) runtime.render(samples.data(), needed);
            std::size_t cursor = 0;
            for (std::size_t n = offset; n < offset + count; ++n) {
                const double fraction = static_cast<double>(phase_) / rate;
                const auto l = static_cast<float>(static_cast<double>(current_.left) * (1 - fraction) + static_cast<double>(next_.left) * fraction);
                const auto r = static_cast<float>(static_cast<double>(current_.right) * (1 - fraction) + static_cast<double>(next_.right) * fraction);
                output[n * 2] = std::isfinite(l) ? std::clamp(l, -1.0f, 1.0f) : 0;
                output[n * 2 + 1] = std::isfinite(r) ? std::clamp(r, -1.0f, 1.0f) : 0;
                phase_ += transportRate;
                while (phase_ >= static_cast<std::uint64_t>(rate)) {
                    phase_ -= rate; current_ = next_; next_ = {samples[cursor * 2], samples[cursor * 2 + 1]}; ++cursor;
                }
            }
            offset += count;
        }
    }
};
}
