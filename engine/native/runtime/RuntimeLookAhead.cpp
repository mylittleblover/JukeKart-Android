#include "runtime/EngineRuntime.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
namespace player2 {
void EngineRuntime::addFutureClipPages(const AudioBuffer* buffer,std::int64_t startSample,std::int64_t maxSample) noexcept {
    if(!buffer || !buffer->stream || maxSample<0)return;
    const auto clampedStart=std::clamp<std::int64_t>(startSample,0,maxSample);
    constexpr auto framesPerPage=static_cast<std::int64_t>(audio::SourcePageCache::FramesPerPage);
    const auto basePage=clampedStart/framesPerPage;
    auto pushPage=[&](std::int64_t sample,bool mandatory) {
        const auto page=sample/framesPerPage;
        for(std::size_t n=0;n<futureClipCount_;++n) {
            const auto& old=futureClipRequirements_[n];
            if(old.cache==buffer->stream && old.asset==buffer->streamedAsset && old.sample.value/framesPerPage==page)return;
        }
        if(mandatory) {
            if(futureClipCount_==futureClipRequirements_.size()){futureClipOverflow_=true;return;}
        } else {
            if(futureClipCount_+4>=futureClipRequirements_.size())return;
        }
        futureClipRequirements_[futureClipCount_++]={buffer->stream,buffer->streamedAsset,{sample},0,std::nullopt};
    };
    pushPage(clampedStart,true);
    for(std::int64_t ahead=1;ahead<=3;++ahead) {
        const auto nextSample=(basePage+ahead)*framesPerPage;
        if(nextSample>maxSample)break;
        pushPage(nextSample,false);
    }
}
void EngineRuntime::collectFutureClip(const Scene& scene,const Track& track,const Clip& clip) noexcept {
    if(!futureClipPreparation_)return;
    const auto* buffer=assets_[clip.asset].get();const auto* asset=find(graph_->assets,clip.asset);
    if(!buffer || !buffer->stream || !asset || asset->sourceLookAhead().value<=0)return;
    // lookAhead is an original Source-sample count. Scene positions are musical
    // Transport-rate frames; resolved Track speed determines Source consumption.
    const long double transportLeadFrames=(static_cast<long double>(clip.start)-scene.position)/parameters_->property(scene.segment->index,1);
    const long double sourceLeadSamples=transportLeadFrames*parameters_->property(track.index,1)*buffer->sampleRate/rate_;
    if(sourceLeadSamples>asset->sourceLookAhead().value)return;
    const auto maxSample=std::min(buffer->frames()-1,clip.sourceOffset+clip.sourceLength-1);
    addFutureClipPages(buffer,clip.sourceOffset,maxSample);
}
void EngineRuntime::collectSegmentLoopFutureClips(const Scene& scene) noexcept {
    if(!futureClipPreparation_ || !scene.segment || scene.preentry || scene.disableLoop)return;
    const auto& segment=*scene.segment;
    if(!segment.hasLoop)return;
    if(segment.loop.count>=0 && scene.loopCount>=segment.loop.count)return;
    const double segRate=std::max(0.0001,parameters_->property(segment.index,1));
    const long double remainingFrames=(static_cast<long double>(segment.loop.end)-scene.position)/segRate;
    if(remainingFrames<0 || remainingFrames>static_cast<long double>(rate_)*2.5L)return;
    for(const auto* trackPointer:tracksBySegment_[segment.index]) {
        const auto& track=*trackPointer;
        const double trackSpeed=parameters_->property(track.index,1);
        const auto inspectClip=[&](const Clip& clip) {
            const auto* buffer=assets_[clip.asset].get();
            if(!buffer || !buffer->stream)return;
            const auto maxSample=std::min(buffer->frames()-1,clip.sourceOffset+clip.sourceLength-1);
            if(maxSample<0)return;
            if(clip.start<=segment.loop.start) {
                const long double catchup=static_cast<long double>(segment.loop.start-clip.start);
                const long double progress=catchup*trackSpeed/segRate*buffer->sampleRate/rate_;
                if(clip.loop && ClipPlayback::remaining(*clip.loop,clip.sourceLength,progress)<=0)return;
                const long double sample=clip.loop
                    ? static_cast<long double>(ClipPlayback::position(*clip.loop,clip.sourceOffset,progress))
                    : static_cast<long double>(clip.sourceOffset)+progress;
                if(!std::isfinite(static_cast<double>(sample)) || sample<0 || sample>static_cast<long double>(maxSample))return;
                addFutureClipPages(buffer,static_cast<std::int64_t>(sample),maxSample);
            } else if(static_cast<long double>(clip.start-segment.loop.start)/segRate<=static_cast<long double>(rate_)*2.0L) {
                addFutureClipPages(buffer,clip.sourceOffset,maxSample);
            }
        };
        if(track.mode==TrackMode::Normal) {
            for(const auto& c:track.clips)inspectClip(c);
        } else if(track.mode==TrackMode::Switch) {
            const int selected=scene.selections[track.index];
            if(selected>=0 && static_cast<std::size_t>(selected)<track.subTracks.size()) {
                for(const auto& c:track.subTracks[selected].clips)inspectClip(c);
            }
        } else {
            for(const auto& sub:track.subTracks) {
                for(const auto& c:sub.clips)inspectClip(c);
            }
        }
    }
}
void EngineRuntime::prepareFutureClips() noexcept {
    if(!futureClipPreparation_)return;
    if(!futureClipOverflow_ && futureClipCount_==0 && futureClipPreparation_->size()==0) {
        futureClipLimited_=false;futureClipReady_=true;return;
    }
    const bool configured=!futureClipOverflow_ && futureClipPreparation_->configure(futureClipRequirements_.data(),futureClipCount_);
    if(!configured) {
        // Drop stale reservations at frame end; never steal leases from playing
        // Voices or the independent Transition/Stinger plans. Retry next frame.
        futureClipPreparation_->retire();futureClipReady_=false;last_=api::EngineStatus::ResourceLimit;
        if(!futureClipLimited_ && futureClipResourceLimits_<static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))++futureClipResourceLimits_;
        futureClipLimited_=true;return;
    }
    futureClipLimited_=false;futureClipReady_=futureClipCount_==0 || futureClipPreparation_->ready();
}
}
