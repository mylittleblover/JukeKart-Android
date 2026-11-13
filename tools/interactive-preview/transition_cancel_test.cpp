// Compare cancellation against uninterrupted source PCM across real loop boundaries.
#include "api/EngineApi.hpp"
#include <algorithm>
#include <cmath>
#include <fstream>
#include <iostream>
#include <iterator>
#include <memory>
#include <stdexcept>
#include <vector>
using namespace player2::api;
static std::vector<std::uint8_t> read(const char* path) {
    std::ifstream f(path,std::ios::binary);
    if(!f) throw std::runtime_error("Missing input");
    return {std::istreambuf_iterator<char>(f),{}};
}
int main(int argc,char** argv) try {
    // graph source target request sync count assets...
    if(argc<8) throw std::runtime_error("graph source target request sync count assets...");
    auto graph=read(argv[1]);const int source=std::stoi(argv[2]),target=std::stoi(argv[3]);
    const auto request=std::stoll(argv[4]),sync=std::stoll(argv[5]);const int count=std::stoi(argv[6]);
    if(argc!=7+count || request<1 || request>=sync) throw std::runtime_error("Invalid arguments");
    std::vector<std::vector<std::uint8_t>> assets;
    for(int i=0;i<count;++i) assets.push_back(read(argv[7+i]));
    auto create=[&]() {
        std::unique_ptr<EngineHandle,decltype(&destroyEngine)> engine(createEngine(),destroyEngine);
        if(!engine) throw std::runtime_error("Allocation");
        if(loadCompiledGraph(engine.get(),graph.data(),graph.size())!=LoadResult::Ok) throw std::runtime_error("Graph");
        for(int i=0;i<count;++i) {
            SourceOpenRequest r;r.providerId="native.bwav";r.bytes={assets[i].data(),assets[i].size()};
            if(bindEngineSource(engine.get(),i,r,SourcePreparation::Streamed)!=EngineStatus::Ok) throw std::runtime_error("Bind");
        }
        return engine;
    };
    auto command=[](EngineHandle* engine,EngineCommandKind kind,int id=-1) {
        EngineCommand c;c.kind=kind;c.target=id;
        const auto status=submitEngineCommand(engine,c);
        if(status!=EngineStatus::Ok) throw std::runtime_error("Command submit status="+std::to_string(static_cast<int>(status)));
    };
    for(const auto offset: {-1,0,1}) {
        auto engine=create(),reference=create();
        std::vector<float> pcm(1024),referencePcm(1024);
        std::int64_t cursor=0;bool cancellationSent=false;
        double maxPcmError=0;
        auto render=[&](std::int64_t frames) {
            if(renderEngineControl(engine.get(),pcm.data(),frames)!=EngineStatus::Ok ||
               renderEngineControl(reference.get(),referencePcm.data(),frames)!=EngineStatus::Ok) throw std::runtime_error("Render");
            if(offset<=0 && cancellationSent) {
                for(int i=0;i<frames*2;++i) {
                    if(!std::isfinite(pcm[i]) || !std::isfinite(referencePcm[i])) throw std::runtime_error("Nonfinite PCM");
                    maxPcmError=std::max(maxPcmError,static_cast<double>(std::abs(pcm[i]-referencePcm[i])));
                }
                if(maxPcmError>0.000001) throw std::runtime_error("Cancelled source PCM differs from uninterrupted reference");
                if(engineSnapshot(engine.get()).position.value!=engineSnapshot(reference.get()).position.value)
                    throw std::runtime_error("Cancellation changed transport position");
            }
        };
        auto until=[&](std::int64_t end) {
            while(cursor<end) { const auto frames=std::min<std::int64_t>(512,end-cursor);render(frames);cursor+=frames; }
        };
        command(engine.get(),EngineCommandKind::Play,source);command(reference.get(),EngineCommandKind::Play,source);
        until(request);command(engine.get(),EngineCommandKind::Play,target);until(sync+offset);
        const bool paused=offset==-1;
        if(paused) { command(engine.get(),EngineCommandKind::Pause);command(reference.get(),EngineCommandKind::Pause);render(1); }
        const auto beforeCancel=engineSnapshot(engine.get()).position.value;
        command(engine.get(),static_cast<EngineCommandKind>(17));cancellationSent=true;
        if(paused) {
            render(1);
            if(engineSnapshot(engine.get()).position.value!=beforeCancel) throw std::runtime_error("Paused clock advanced");
            command(engine.get(),EngineCommandKind::Resume);command(reference.get(),EngineCommandKind::Resume);
        }
        until(sync+offset+1);
        const auto snapshot=engineSnapshot(engine.get());
        if(offset<=0) {
            if(snapshot.lastResult!=EngineStatus::Ok || snapshot.rejectedCommands || snapshot.playingSegment!=source || snapshot.transition[0])
                throw std::runtime_error("Pending cancellation failed");
            // An exhausted Voice may be absent at exact EOF. Render the next loop before comparing cursors.
            until(sync+1024);
            const auto actual=engineSnapshot(engine.get()),baseline=engineSnapshot(reference.get());
            if(actual.playingSegment!=source || actual.activeVoices!=baseline.activeVoices) throw std::runtime_error("Source did not continue");
            std::vector<std::int64_t> actualPositions,referencePositions;
            for(int slot=0;slot<128;++slot) {
                const auto a=engineSourceSnapshot(engine.get(),slot),b=engineSourceSnapshot(reference.get(),slot);
                if(a[1] && a[4]==source) actualPositions.push_back(a[10]);
                if(b[1] && b[4]==source) referencePositions.push_back(b[10]);
            }
            std::sort(actualPositions.begin(),actualPositions.end());std::sort(referencePositions.begin(),referencePositions.end());
            if(actualPositions.empty() || actualPositions!=referencePositions) throw std::runtime_error("Cancellation restarted source phase");
        } else if(snapshot.lastResult!=EngineStatus::InvalidRequest || snapshot.playingSegment!=target || snapshot.activeVoices!=1) {
            throw std::runtime_error("Started target was cancelled or not rejected");
        }
        const auto streaming=engineStreamingSnapshot(engine.get());
        if(streaming.underrunFrames || streaming.decodeFailures) throw std::runtime_error("Stream error");
        std::cout<<"offset="<<offset<<" segment="<<snapshot.playingSegment<<" result="<<static_cast<int>(snapshot.lastResult)
                 <<" voices="<<snapshot.activeVoices<<" maxPcmError="<<maxPcmError<<" PASS\n";
    }
    return 0;
} catch(const std::exception& e) { std::cerr<<e.what()<<"\n";return 1; }
