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
static std::vector<std::uint8_t> read(const char* p) { std::ifstream f(p,std::ios::binary); if(!f)throw std::runtime_error("input");return {std::istreambuf_iterator<char>(f),{}}; }
int main(int argc,char** argv) try {
 if(argc<5)throw std::runtime_error("graph segment bpm assets...");
 auto graph=read(argv[1]);std::vector<std::vector<std::uint8_t>> assets;
 for(int i=4;i<argc;++i)assets.push_back(read(argv[i]));
 auto make=[&](){std::unique_ptr<EngineHandle,decltype(&destroyEngine)> e(createEngine(),destroyEngine);
  if(loadCompiledGraph(e.get(),graph.data(),graph.size())!=LoadResult::Ok)throw std::runtime_error("graph");
  for(size_t i=0;i<assets.size();++i){SourceOpenRequest r;r.providerId="native.bwav";r.bytes={assets[i].data(),assets[i].size()};if(bindEngineSource(e.get(),i,r,SourcePreparation::Streamed)!=EngineStatus::Ok)throw std::runtime_error("bind");}return e;};
 auto e=make(),ref=make();auto send=[](EngineHandle* e,EngineCommandKind k,int target=-1,double n=0){EngineCommand c;c.kind=k;c.target=target;c.number=n;if(submitEngineCommand(e,c)!=EngineStatus::Ok)throw std::runtime_error("command");};
 send(e.get(),EngineCommandKind::Play,std::stoi(argv[2]));send(ref.get(),EngineCommandKind::Play,std::stoi(argv[2]));
 std::vector<float> out(192),expected(192);
 auto render=[&](int n){if(renderEngineControl(e.get(),out.data(),n)!=EngineStatus::Ok||renderEngineControl(ref.get(),expected.data(),n)!=EngineStatus::Ok)throw std::runtime_error("render");};
 for(int i=0;i<50;++i)render(96);
 send(e.get(),EngineCommandKind::Stop,-1,std::stod(argv[3])/60.0); // exactly one second
 double error=0;int cursor=0;
 while(cursor<48096){
  if(cursor==12000){
   send(e.get(),EngineCommandKind::Pause);send(ref.get(),EngineCommandKind::Pause);render(96);
   auto frame=engineSnapshot(e.get()).position.value;
   for(int i=0;i<20;++i){render(96);if(engineSnapshot(e.get()).position.value!=frame)throw std::runtime_error("paused clock advanced");for(float x:out)if(x!=0)throw std::runtime_error("pause not silent");}
   send(e.get(),EngineCommandKind::Resume);send(ref.get(),EngineCommandKind::Resume);
  }
  render(96);
  if(engineSnapshot(e.get()).globalFadeEnd!=52800)throw std::runtime_error("public snapshot lost exact fade deadline");
  for(int i=0;i<96;++i){double gain=std::max(0.0,1.0-double(cursor+i)/48000.0);for(int ch=0;ch<2;++ch)error=std::max(error,std::abs(double(out[i*2+ch])-expected[i*2+ch]*gain));}
  cursor+=96;
 }
 if(error>0.00001)throw std::runtime_error("fade PCM mismatch "+std::to_string(error));
 if(engineSnapshot(e.get()).activeVoices!=0)throw std::runtime_error("voices remain");
 std::cout<<"PASS sample-accurate one-second fade; pause freezes clock; peak PCM error="<<error<<"\n";
 return 0;
} catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}
