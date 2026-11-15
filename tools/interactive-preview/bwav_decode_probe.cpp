#include "api/AudioSourceTypes.hpp"
#include "audio/source/AudioFrameSource.hpp"
#include "decoder/BwavFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <cstdio>
#include <chrono>
#include <cmath>
#include <fstream>
#include <iterator>
#include <memory>
#include <string>
#include <vector>

using namespace player2::audio;

static const char* statusName(SourceStatus status) {
    switch (status) {
        case SourceStatus::Ok: return "Ok";
        case SourceStatus::UnsupportedFormat: return "UnsupportedFormat";
        case SourceStatus::MalformedSource: return "MalformedSource";
        case SourceStatus::ResourceLimit: return "ResourceLimit";
        case SourceStatus::UnsupportedCodec: return "UnsupportedCodec";
        case SourceStatus::UnsupportedChannels: return "UnsupportedChannels";
        case SourceStatus::UnsupportedSampleRate: return "UnsupportedSampleRate";
        case SourceStatus::SourceLoopBounds: return "SourceLoopBounds";
        case SourceStatus::SeekUnsupported: return "SeekUnsupported";
        case SourceStatus::EntryMissing: return "EntryMissing";
        case SourceStatus::SubsongMissing: return "SubsongMissing";
        case SourceStatus::VariantMissing: return "VariantMissing";
        case SourceStatus::InvalidRequest: return "InvalidRequest";
        case SourceStatus::InputUnavailable: return "InputUnavailable";
        case SourceStatus::InputChanged: return "InputChanged";
    }
    return "?";
}

int main(int argc, char** argv) {
    if (argc < 2) { std::fprintf(stderr, "usage: probe <file.bwav> [frames]\n"); return 2; }
    std::ifstream in(argv[1], std::ios::binary);
    if (!in) { std::fprintf(stderr, "cannot open %s\n", argv[1]); return 2; }
    std::vector<std::uint8_t> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    std::printf("file        : %s\n", argv[1]);
    std::printf("bytes       : %zu\n", bytes.size());

    DecodeLimits limits;
    // Report what the container says first, so a failure below can be located.
    {
        MemoryEncodedSourceInput probe({bytes.data(), bytes.size()}, limits);
        EncodedSourceBytes view(probe, limits);
        try {
            BwavLayout layout = parseBwav(view, limits);
            std::printf("parse       : Ok  codec=%s channels=%d rate=%d frames=%lld opusRegions=%zu\n",
                layout.dsp ? "dsp" : "pcm/opus", layout.metadata.channels,
                layout.metadata.sampleRate, static_cast<long long>(layout.metadata.duration.value),
                layout.opusChannels.size());
        } catch (const SourceFailure& failure) {
            std::printf("parse       : FAILED %s\n", statusName(failure.status));
            return 1;
        }
    }

    std::unique_ptr<AudioFrameSource> source;
    try {
        source = openBwav({bytes.data(), bytes.size()}, limits);
    } catch (const SourceFailure& failure) {
        std::printf("open        : FAILED %s\n", statusName(failure.status));
        return 1;
    } catch (const std::exception& error) {
        std::printf("open        : FAILED exception %s\n", error.what());
        return 1;
    }
    if (!source) { std::printf("open        : null\n"); return 1; }

    const auto& meta = source->metadata();
    std::printf("open        : Ok channels=%d rate=%d frames=%lld\n",
        meta.channels, meta.sampleRate, static_cast<long long>(meta.duration.value));
    if (meta.sourceLoop) {
        std::printf("loop        : %lld..%lld\n", static_cast<long long>(meta.sourceLoop->start.value),
            static_cast<long long>(meta.sourceLoop->end.value));
    }

    const auto wanted = argc > 2 ? static_cast<std::size_t>(std::atoll(argv[2])) : std::size_t(48000);
    std::vector<float> pcm(wanted * static_cast<std::size_t>(meta.channels));
    const auto got = source->render(pcm.data(), wanted);
    std::printf("render      : %zu frames requested, %zu produced, status=%s\n",
        wanted, got, statusName(source->renderStatus()));

    double peak = 0.0, energy = 0.0;
    std::size_t nonZero = 0;
    for (float value : pcm) {
        const double magnitude = std::fabs(static_cast<double>(value));
        if (magnitude > peak) peak = magnitude;
        energy += static_cast<double>(value) * value;
        if (value != 0.0f) ++nonZero;
    }
    std::printf("signal      : peak=%.6f rms=%.6f nonzero=%zu/%zu\n",
        peak, pcm.empty() ? 0.0 : std::sqrt(energy / static_cast<double>(pcm.size())),
        nonZero, pcm.size());

    // Channel separation: the two regions are independent streams, so they must not be identical.
    if (meta.channels == 2 && got > 0) {
        double diff = 0.0, left = 0.0, right = 0.0;
        for (std::size_t n = 0; n < got; ++n) {
            diff += std::fabs(pcm[n * 2] - pcm[n * 2 + 1]);
            left += std::fabs(pcm[n * 2]);
            right += std::fabs(pcm[n * 2 + 1]);
        }
        std::printf("channels    : meanAbsDiff=%.6f meanL=%.6f meanR=%.6f\n",
            diff / static_cast<double>(got), left / static_cast<double>(got), right / static_cast<double>(got));
    }

    // A short seek must land where asked and keep producing signal.
    const auto seekTarget = SourceSamplePosition{static_cast<std::int64_t>(meta.duration.value) / 2};
    const auto beginSeek = std::chrono::steady_clock::now();
    const auto seekStatus = source->seek(seekTarget);
    const auto seekMs = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - beginSeek).count();
    std::printf("seek        : %s to %lld, now at %lld (%.2f ms)\n", statusName(seekStatus),
        static_cast<long long>(seekTarget.value), static_cast<long long>(source->position().value), seekMs);
    std::vector<float> after(wanted * static_cast<std::size_t>(meta.channels));
    const auto gotAfter = source->render(after.data(), wanted);
    double peakAfter = 0.0;
    for (float value : after) peakAfter = std::max(peakAfter, std::fabs(static_cast<double>(value)));
    std::printf("after seek  : %zu frames, peak=%.6f, status=%s\n", gotAfter, peakAfter,
        statusName(source->renderStatus()));
    if (seekStatus != SourceStatus::Ok || gotAfter == 0 || source->renderStatus() != SourceStatus::Ok) return 1;

    const auto tailFrames = static_cast<std::size_t>(std::min<std::int64_t>(256, meta.duration.value));
    if (source->seek({meta.duration.value - static_cast<std::int64_t>(tailFrames)}) != SourceStatus::Ok) return 1;
    std::vector<float> tail(tailFrames * static_cast<std::size_t>(meta.channels));
    const auto tailRendered = source->render(tail.data(), tailFrames);
    std::printf("tail        : %zu/%zu frames, status=%s\n", tailRendered, tailFrames, statusName(source->renderStatus()));
    return tailRendered == tailFrames && source->renderStatus() == SourceStatus::Ok ? 0 : 1;
}
