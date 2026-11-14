#include "api/EngineApi.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <fstream>
#include <iostream>
#include <iterator>
#include <memory>
#include <stdexcept>
#include <vector>
using namespace player2::api;
static std::vector<std::uint8_t> read(const char* p){std::ifstream f(p,std::ios::binary);if(!f)throw std::runtime_error("input");return {std::istreambuf_iterator<char>(f),{}};}
int main(int argc,char** argv)try{
 if(argc<7)throw std::runtime_error("graph source target preparedAsset count assets...");
 auto graph=read(argv[1]);int source=std::stoi(argv[2]),target=std::stoi(argv[3]),prepared=std::stoi(argv[4]),count=std::stoi(argv[5]);
 if(argc!=6+count)throw std::runtime_error("args");std::vector<std::vector<std::uint8_t>> data;
 for(int i=0;i<count;++i)data.push_back(read(argv[6+i]));
 auto make=[&](){std::unique_ptr<EngineHandle,decltype(&destroyEngine)> e(createEngine(),destroyEngine);if(loadCompiledGraph(e.get(),graph.data(),graph.size())!=LoadResult::Ok)throw std::runtime_error("graph");for(int i=0;i<count;++i){SourceOpenRequest r;r.providerId="native.bwav";r.bytes={data[i].data(),data[i].size()};if(bindEngineSource(e.get(),i,r,i==prepared?SourcePreparation::Prepared:SourcePreparation::Streamed)!=EngineStatus::Ok)throw std::runtime_error("bind");}return e;};
 for(int mode=0;mode<2;++mode){auto e=make();EngineCommand c;c.kind=EngineCommandKind::Play;c.target=source;submitEngineCommand(e.get(),c);std::vector<float> pcm(512);
  for(int n=0;n<100;++n)renderEngineRealtimeHarness(e.get(),pcm.data(),256);
  if(mode){c.target=target;submitEngineCommand(e.get(),c);}
  auto start=std::chrono::steady_clock::now();long long frames=0;
  for(int n=0;n<1875;++n){if(renderEngineRealtimeHarness(e.get(),pcm.data(),256)!=EngineStatus::Ok)throw std::runtime_error("render");frames+=256;}
  double seconds=std::chrono::duration<double>(std::chrono::steady_clock::now()-start).count();auto s=engineSnapshot(e.get());auto cache=engineStreamingSnapshot(e.get());
  if(s.rejectedCommands||cache.underrunFrames||cache.decodeFailures)throw std::runtime_error("audio failure");
  if(mode && !s.transition[0])throw std::runtime_error("transition is not pending");
  std::cout<<(mode?"pending":"steady")<<" seconds="<<seconds<<" nsPerFrame="<<seconds*1e9/frames<<" pending="<<s.transition[0]<<" underruns="<<cache.underrunFrames<<"\n";
 }
 return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<"\n";return 1;}
