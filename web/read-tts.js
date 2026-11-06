// 朗读（B8-c）：优先浏览器 speechSynthesis，零密钥零成本零出网。
// win 可注入（测试喂假 synth）；不支持时 supported=false，UI 侧整体隐藏入口。
(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.ReadTts = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const createSpeaker = function (win) {
    const synth = win && win.speechSynthesis;
    const Utterance = win && win.SpeechSynthesisUtterance;
    const supported = Boolean(synth && typeof synth.speak === "function" && typeof Utterance === "function");
    let current = null;

    // 仅在确有在读 utterance 时才 cancel：空闲 stop 是无害空操作，
    // 否则每次启动朗读的前置清理都会白打一次 cancel，计数与状态都脏。
    const stop = function () {
      if (!supported || !current) return;
      try {
        synth.cancel();
      } catch (error) {
        // 某些实现 cancel 会抛（如已空闲），朗读失败不应打断阅读。
      }
      current = null;
    };

    // speak 返回 false 表示没读起来（不支持或空文本）；onEnd 在读完/出错/被打断收尾时触发。
    const speak = function (text, rate, onEnd) {
      if (!supported || !text) return false;
      stop();
      const utterance = new Utterance(text);
      utterance.lang = "ja-JP";
      utterance.rate = typeof rate === "number" && rate > 0 ? rate : 1;
      const finish = function () {
        if (current === utterance) current = null;
        if (typeof onEnd === "function") onEnd();
      };
      utterance.onend = finish;
      utterance.onerror = finish;
      current = utterance;
      synth.speak(utterance);
      return true;
    };

    const pause = function () {
      if (!supported || !current) return false;
      if (typeof synth.pause === "function") {
        synth.pause();
        return true;
      }
      return false;
    };

    const resume = function () {
      if (!supported || !current) return false;
      if (typeof synth.resume === "function") {
        synth.resume();
        return true;
      }
      return false;
    };

    const speaking = function () {
      return Boolean(current) && (typeof synth.speaking !== "boolean" || synth.speaking);
    };

    return {
      supported: supported,
      speak: speak,
      stop: stop,
      pause: pause,
      resume: resume,
      speaking: speaking
    };
  };

  return {
    createSpeaker: createSpeaker
  };
});
