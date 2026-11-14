// Records the same Engine API used by Android. No splicing or independent mixer.
#include "api/EngineApi.hpp"
#include <fstream>
#include <iostream>
#include <vector>
#include <string>
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <sstream>
using namespace player2;
std::vector<std::uint8_t> read(const std::string& path) {
    std::ifstream f(path,std::ios::binary);
    if(!f)throw std::runtime_error("Cannot open "+path);
    return {std::istreambuf_iterator<char>(f),std::istreambuf_iterator<char>()};
}
void word(std::ostream& f,std::uint32_t x,int bytes) {for(int i=0;i<bytes;++i)f.put(static_cast<char>(x>>(8*i)));}
int main(int argc,char** argv) try {
    // graph, output.wav, durationFrames, assetCount, assets..., [requestFrame,target]...
    if(argc<6)throw std::runtime_error("graph output.wav frames assetCount assets... [requestFrame,target]...");
    const auto packet=read(argv[1]); const auto duration=std::stoll(argv[3]); const int count=std::stoi(argv[4]);
    if(duration<=0 || count<1 || argc<5+count)throw std::runtime_error("Invalid arguments");
    auto* engine=api::createEngine();if(!engine)throw std::runtime_error("Engine allocation failed");
    struct Guard{api::EngineHandle* h;~Guard(){api::destroyEngine(h);}} guard{engine};
    if(api::loadCompiledGraph(engine,packet.data(),packet.size())!=api::LoadResult::Ok)throw std::runtime_error("Invalid graph");
    std::vector<std::vector<std::uint8_t>> assets;
    for(int i=0;i<count;++i) {
        assets.push_back(read(argv[5+i]));auto& b=assets.back();api::SourceOpenRequest req;
        req.providerId="native.bwav";req.bytes={b.data(),b.size()};
        const bool streamed=std::getenv("MKW_SOURCE_PREPARATION") && std::string(std::getenv("MKW_SOURCE_PREPARATION"))=="streamed";
        bool preparedOverride=false;
        if (const auto* values=std::getenv("MKW_PREPARED_INDICES")) {
            std::istringstream input(values); std::string item;
            while (std::getline(input,item,',')) if (!item.empty() && std::stoi(item)==i) preparedOverride=true;
        }
        const auto bound=api::bindEngineSource(engine,i,req,streamed && !preparedOverride ? api::SourcePreparation::Streamed : api::SourcePreparation::Prepared);
        if(bound!=api::EngineStatus::Ok) {auto probe=api::openAudioSource(req);if(probe.handle)api::closeAudioSource(probe.handle);throw std::runtime_error("Binding failed "+std::string(argv[5+i])+" engine="+std::to_string(static_cast<int>(bound))+" source="+std::to_string(static_cast<int>(probe.status)));}
    }
    std::vector<std::pair<std::int64_t,int>> actions;
    for(int i=5+count;i<argc;++i){std::string a=argv[i];auto c=a.find(',');actions.emplace_back(std::stoll(a.substr(0,c)),std::stoi(a.substr(c+1)));}
    std::stable_sort(actions.begin(),actions.end(),[](const auto& a,const auto& b){return a.first<b.first;});std::size_t action=0;
    const auto recordStart=std::getenv("MKW_RECORD_START") ? std::stoll(std::getenv("MKW_RECORD_START")) : 0;
    if(recordStart<0 || recordStart>=duration)throw std::runtime_error("Invalid recording range");
    const auto recordingFrames=duration-recordStart;
    if(recordingFrames*4>0xffffffffULL-36)throw std::runtime_error("Preview exceeds WAV limit");
    std::ofstream wav(argv[2],std::ios::binary);wav.write("RIFF",4);word(wav,36+recordingFrames*4,4);wav.write("WAVEfmt ",8);word(wav,16,4);word(wav,1,2);word(wav,2,2);word(wav,48000,4);word(wav,192000,4);word(wav,4,2);word(wav,16,2);wav.write("data",4);word(wav,recordingFrames*4,4);
    std::vector<float> pcm(512*2);std::int64_t cursor=0;int last=-999,lastVoices=-1,lastPhase=-999;std::array<bool,4096> seenSegments{};std::int64_t lastRequest=-999;double peak=0;std::uint64_t clipped=0;
    while(cursor<duration){
        while(action<actions.size() && actions[action].first==cursor){api::EngineCommand c;c.kind=api::EngineCommandKind::Play;c.target=actions[action++].second;
            if(api::submitEngineCommand(engine,c)!=api::EngineStatus::Ok)throw std::runtime_error("Command rejected");}
        auto n=std::min<std::int64_t>(512,duration-cursor);
        if(action<actions.size())n=std::min(n,actions[action].first-cursor);
        if(n<=0)throw std::runtime_error("Invalid request frame");
        if(api::renderEngineControl(engine,pcm.data(),n)!=api::EngineStatus::Ok)throw std::runtime_error("Render failed");
        const auto s=api::engineSnapshot(engine);
        if(s.rejectedCommands || s.lastResult!=api::EngineStatus::Ok)throw std::runtime_error("Native result "+std::to_string(static_cast<int>(s.lastResult)));
        if(s.transition[0] && s.transition[5]!=lastRequest) {
            std::cout<<"plan request="<<s.transition[5]<<" sourceSync="<<s.transition[6]<<" destinationEntry="<<s.transition[7]<<" firstStart="<<s.transition[8]<<"\n";lastRequest=s.transition[5];
        }
        if(s.activeVoices!=lastVoices || s.playingSegment!=last || s.transition[13]!=lastPhase) {
            for(int slot=0;slot<128;++slot) {
                const auto v=api::engineSourceSnapshot(engine,slot);
                if(v[1] && v[4]>=0 && v[4]<4096 && !seenSegments[v[4]]) {
                    std::cout<<"voice segment="<<v[4]<<" at="<<v[27]<<" source="<<v[10]<<"\n";
                    seenSegments[v[4]]=true;
                }
            }
            lastVoices=s.activeVoices;lastPhase=s.transition[13];
        }
        if(s.playingSegment!=last){std::cout<<"frame="<<s.position.value<<" segment="<<s.playingSegment<<" voices="<<s.activeVoices<<"\n";last=s.playingSegment;}
        for(int i=0;i<n*2;++i){if(!std::isfinite(pcm[i]))throw std::runtime_error("Non-finite PCM");peak=std::max(peak,static_cast<double>(std::abs(pcm[i])));if(std::abs(pcm[i])>1)++clipped;if(cursor+i/2>=recordStart)word(wav,static_cast<std::uint16_t>(static_cast<std::int16_t>(std::lrint(std::clamp(pcm[i],-1.f,1.f)*32767))),2);}
        cursor+=n;
    }
    if(!wav)throw std::runtime_error("WAV write failed");
    const auto streaming=api::engineStreamingSnapshot(engine);
    if(streaming.underrunFrames || streaming.decodeFailures)throw std::runtime_error("Stream underrun/decode failure");
    std::cout<<"streamUnderrunFrames="<<streaming.underrunFrames<<" decodeFailures="<<streaming.decodeFailures<<"\n";
    std::cout<<"frames="<<cursor<<" peak="<<peak<<" clipped="<<clipped<<"\n";
    return 0;
} catch(const std::exception& e) {std::cerr<<e.what()<<"\n";return 1;}
