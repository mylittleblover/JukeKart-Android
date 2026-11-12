// Opt-in Native regression harness for a finite source fade at a segment boundary.
#include "api/EngineApi.hpp"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <string>
#include <vector>

using namespace player2;
using namespace player2::api;

static std::vector<std::uint8_t> readBytes(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) throw std::runtime_error("Cannot open " + path);
    return {std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>()};
}

int main(int argc, char** argv) try {
    // graph sourceSegment targetSegment sourceSync fadeFrames requestLeadFrames assetCount assets...
    if (argc < 9) throw std::runtime_error(
        "Usage: mkw_rainbow_fade_boundary_test graph sourceSegment targetSegment sourceSync fadeFrames requestLeadFrames assetCount assets...");
    const auto graph = readBytes(argv[1]);
    const int sourceSegment = std::stoi(argv[2]);
    const int targetSegment = std::stoi(argv[3]);
    const std::int64_t sourceSync = std::stoll(argv[4]);
    const std::int64_t fadeFrames = std::stoll(argv[5]);
    const std::int64_t requestLeadFrames = std::stoll(argv[6]);
    const int assetCount = std::stoi(argv[7]);
    if (sourceSync <= 0 || fadeFrames <= 0 || requestLeadFrames <= 0 || assetCount <= 0 || argc != 8 + assetCount)
        throw std::runtime_error("Invalid boundary-test arguments");
    const auto requestFrame = sourceSync - requestLeadFrames;
    if (requestFrame <= 0) throw std::runtime_error("Request lead must leave a positive request frame");

    std::vector<std::vector<std::uint8_t>> assets;
    assets.reserve(assetCount);
    for (int i = 0; i < assetCount; ++i) assets.push_back(readBytes(argv[8 + i]));
    const bool streamed = std::getenv("MKW_SOURCE_PREPARATION") &&
        std::string(std::getenv("MKW_SOURCE_PREPARATION")) == "streamed";
    auto prepareEngine = [&](EngineHandle* handle) {
        if (!handle) throw std::runtime_error("Engine allocation failed");
        if (loadCompiledGraph(handle, graph.data(), graph.size()) != LoadResult::Ok)
            throw std::runtime_error("Compiled graph failed to load");
        for (int i = 0; i < assetCount; ++i) {
            SourceOpenRequest request;
            request.providerId = "native.bwav";
            request.bytes = {assets[i].data(), assets[i].size()};
            const auto bound = bindEngineSource(handle, i, request,
                streamed ? SourcePreparation::Streamed : SourcePreparation::Prepared);
            if (bound != EngineStatus::Ok) throw std::runtime_error("Native source bind failed: " + std::to_string(static_cast<int>(bound)));
        }
    };
    if (std::getenv("MKW_TEST_CANCEL_DEPARTURE") && std::string(std::getenv("MKW_TEST_CANCEL_DEPARTURE")) == "1") {
        auto* cancelEngine = createEngine();
        struct CancelGuard { EngineHandle* handle; ~CancelGuard() { destroyEngine(handle); } } cancelGuard{cancelEngine};
        prepareEngine(cancelEngine);
        EngineCommand command; command.kind = EngineCommandKind::Play; command.target = sourceSegment;
        if (submitEngineCommand(cancelEngine, command) != EngineStatus::Ok)
            throw std::runtime_error("Native source play rejected in cancellation proof");
        std::vector<float> cancelPcm(512 * 2);
        std::int64_t cancelCursor = 0;
        while (cancelCursor < requestFrame) {
            const auto count = std::min<std::int64_t>(512, requestFrame - cancelCursor);
            if (renderEngineControl(cancelEngine, cancelPcm.data(), static_cast<std::size_t>(count)) != EngineStatus::Ok)
                throw std::runtime_error("Native source render failed in cancellation proof");
            cancelCursor += count;
        }
        command.target = targetSegment;
        if (submitEngineCommand(cancelEngine, command) != EngineStatus::Ok ||
            renderEngineControl(cancelEngine, cancelPcm.data(), 1) != EngineStatus::Ok)
            throw std::runtime_error("Native departure request failed in cancellation proof");
        auto departureState = engineSnapshot(cancelEngine);
        bool sourceRepeating = false, sourceActive = false;
        for (int slot = 0; slot < 128; ++slot) {
            const auto voice = engineSourceSnapshot(cancelEngine, slot);
            if (voice[1] && voice[4] == sourceSegment) { sourceActive = true; sourceRepeating = voice[9]; }
        }
        if (!departureState.transition[0] || !sourceActive || !sourceRepeating)
            throw std::runtime_error("Departure did not retain and repeat its active source before cancellation");
        std::cout << "cancelProof beforeCancel frame=" << departureState.position.value
                  << " transition=" << departureState.transition[0] << " sourceActive=" << sourceActive
                  << " sourceRepeating=" << sourceRepeating << "\n";
        command.target = 4094; // Known unused target: processing this request cancels the pending departure.
        if (submitEngineCommand(cancelEngine, command) != EngineStatus::Ok ||
            renderEngineControl(cancelEngine, cancelPcm.data(), 1) != EngineStatus::Ok)
            throw std::runtime_error("Invalid cancellation request could not be rendered");
        const auto cancelledState = engineSnapshot(cancelEngine);
        sourceRepeating = false; sourceActive = false;
        for (int slot = 0; slot < 128; ++slot) {
            const auto voice = engineSourceSnapshot(cancelEngine, slot);
            if (voice[1] && voice[4] == sourceSegment) { sourceActive = true; sourceRepeating = voice[9]; }
        }
        if (cancelledState.lastResult != EngineStatus::InvalidRequest || cancelledState.transition[0] ||
            !sourceActive || sourceRepeating)
            throw std::runtime_error("Cancelled departure did not restore source repeat state and clear transition");
        std::cout << "cancelProof afterCancel frame=" << cancelledState.position.value
                  << " result=" << static_cast<int>(cancelledState.lastResult)
                  << " transition=" << cancelledState.transition[0] << " sourceActive=" << sourceActive
                  << " sourceRepeating=" << sourceRepeating << "\n";
    }
    auto* engine = createEngine();
    struct Guard { EngineHandle* handle; ~Guard() { destroyEngine(handle); } } guard{engine};
    prepareEngine(engine);

    struct Action { std::int64_t frame; int target; };
    const Action actions[] = {{0, sourceSegment}, {requestFrame, targetSegment}};
    std::size_t action = 0;
    std::int64_t cursor = 0;
    std::vector<float> pcm(512 * 2);
    double peak = 0.0;
    std::uint64_t clipped = 0;
    std::int64_t underrunFrames = 0, decodeFailures = 0;

    auto snapshotAt = [&](std::int64_t frame, bool requireBoth) {
        while (cursor < frame) {
            while (action < 2 && actions[action].frame == cursor) {
                EngineCommand command; command.kind = EngineCommandKind::Play; command.target = actions[action++].target;
                const auto status = submitEngineCommand(engine, command);
                if (status != EngineStatus::Ok) throw std::runtime_error("Native command rejected: " + std::to_string(static_cast<int>(status)));
            }
            auto count = std::min<std::int64_t>(512, frame - cursor);
            if (action < 2) count = std::min(count, actions[action].frame - cursor);
            if (count <= 0) throw std::runtime_error("Invalid Native render slice");
            if (renderEngineControl(engine, pcm.data(), static_cast<std::size_t>(count)) != EngineStatus::Ok)
                throw std::runtime_error("Native render failed");
            for (int i = 0; i < count * 2; ++i) {
                if (!std::isfinite(pcm[i])) throw std::runtime_error("Non-finite PCM");
                peak = std::max(peak, static_cast<double>(std::abs(pcm[i])));
                if (std::abs(pcm[i]) > 1.0f) ++clipped;
            }
            cursor += count;
        }
        const auto engineState = engineSnapshot(engine);
        if (engineState.position.value != frame || engineState.rejectedCommands || engineState.lastResult != EngineStatus::Ok)
            throw std::runtime_error("Native snapshot/result mismatch at " + std::to_string(frame));
        if (frame == sourceSync && (!engineState.transition[0] || engineState.transition[6] != sourceSync))
            throw std::runtime_error("Expected an active compiled transition at sourceSync=" + std::to_string(sourceSync));
        int sourceVoices = 0, targetVoices = 0;
        std::vector<int> activeSegments;
        for (int slot = 0; slot < 128; ++slot) {
            const auto voice = engineSourceSnapshot(engine, slot);
            if (!voice[1]) continue;
            const int segment = static_cast<int>(voice[4]);
            activeSegments.push_back(segment);
            if (segment == sourceSegment) ++sourceVoices;
            if (segment == targetSegment) ++targetVoices;
        }
        if (requireBoth && (engineState.activeVoices != 2 || sourceVoices != 1 || targetVoices != 1))
            throw std::runtime_error("Expected exactly 2 voices (one source, one target) at " + std::to_string(frame));
        if (!requireBoth && frame > sourceSync &&
            (engineState.activeVoices != 1 || sourceVoices != 0 || targetVoices != 1))
            throw std::runtime_error("Expected exactly 1 target voice after fade at " + std::to_string(frame));
        const auto streaming = engineStreamingSnapshot(engine);
        underrunFrames = streaming.underrunFrames;
        decodeFailures = streaming.decodeFailures;
        std::cout << "frame=" << frame << " voices=" << engineState.activeVoices
                  << " sourceVoices=" << sourceVoices << " targetVoices=" << targetVoices
                  << " sourceSegments=";
        for (std::size_t i = 0; i < activeSegments.size(); ++i) std::cout << (i ? "," : "") << activeSegments[i];
        std::cout << "\n";
    };

    snapshotAt(sourceSync, false); // transition is scheduled; the target starts on the sourceSync sample.
    snapshotAt(sourceSync + 1, true);
    snapshotAt(sourceSync + fadeFrames / 2, true);
    snapshotAt(sourceSync + fadeFrames, true); // boundary sample has not rendered yet.
    snapshotAt(sourceSync + fadeFrames + 1, false);
    if (clipped || underrunFrames || decodeFailures)
        throw std::runtime_error("PCM/stream failure clipped=" + std::to_string(clipped) +
            " underrunFrames=" + std::to_string(underrunFrames) + " decodeFailures=" + std::to_string(decodeFailures));
    std::cout << "fadeFrames=" << fadeFrames << " peak=" << peak << " clipped=" << clipped
              << " streamUnderrunFrames=" << underrunFrames << " decodeFailures=" << decodeFailures << "\n";
    return 0;
} catch (const std::exception& error) {
    std::cerr << error.what() << "\n";
    return 1;
}
