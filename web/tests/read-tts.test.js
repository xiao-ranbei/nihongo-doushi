"use strict";

// read-tts 单测（B8-c）：纯本地 speechSynthesis 适配层。
// 假 synth 注入验证——测试零真实语音、零出网。

const assert = require("node:assert/strict");
const test = require("node:test");

const ReadTts = require("../read-tts.js");

function fakeWin(options) {
  const opts = options || {};
  const calls = { speak: [], cancel: 0, pause: 0, resume: 0 };
  function Utterance(text) {
    this.text = text;
  }
  const win = {
    SpeechSynthesisUtterance: opts.noUtterance ? undefined : Utterance,
    speechSynthesis: opts.noSynth ? undefined : {
      speaking: false,
      speak: function (u) {
        calls.speak.push(u);
        this.speaking = true;
      },
      cancel: function () {
        calls.cancel += 1;
        this.speaking = false;
      },
      pause: opts.noPause ? undefined : function () { calls.pause += 1; },
      resume: opts.noResume ? undefined : function () { calls.resume += 1; }
    }
  };
  return { win: win, calls: calls, Utterance: Utterance };
}

test("supported：synth 与 Utterance 齐备才可用", function () {
  assert.equal(ReadTts.createSpeaker(fakeWin().win).supported, true);
  assert.equal(ReadTts.createSpeaker(fakeWin({ noSynth: true }).win).supported, false);
  assert.equal(ReadTts.createSpeaker(fakeWin({ noUtterance: true }).win).supported, false);
  assert.equal(ReadTts.createSpeaker(null).supported, false);
});

test("speak：设定日语与语速，读完触发 onEnd", function () {
  const fake = fakeWin();
  const speaker = ReadTts.createSpeaker(fake.win);
  let ended = 0;
  assert.equal(speaker.speak("こんにちは", 1.5, function () { ended += 1; }), true);
  assert.equal(fake.calls.speak.length, 1);
  const u = fake.calls.speak[0];
  assert.equal(u.text, "こんにちは");
  assert.equal(u.lang, "ja-JP");
  assert.equal(u.rate, 1.5);
  u.onend();
  assert.equal(ended, 1);
});

test("speak：出错也走 onEnd，空文本不读", function () {
  const fake = fakeWin();
  const speaker = ReadTts.createSpeaker(fake.win);
  let ended = 0;
  speaker.speak("あ", 1, function () { ended += 1; });
  fake.calls.speak[0].onerror();
  assert.equal(ended, 1);
  assert.equal(speaker.speak("", 1), false);
  assert.equal(speaker.speak(null, 1), false);
  assert.equal(fake.calls.speak.length, 1);
});

test("speak 连发：新朗读取消旧朗读", function () {
  const fake = fakeWin();
  const speaker = ReadTts.createSpeaker(fake.win);
  speaker.speak("一", 1);
  speaker.speak("二", 1);
  assert.equal(fake.calls.cancel, 1, "第二次 speak 前应 cancel 掉第一次");
  assert.equal(fake.calls.speak.length, 2);
  assert.equal(fake.calls.speak[1].text, "二");
});

test("pause/resume/stop 透传到 synth", function () {
  const fake = fakeWin();
  const speaker = ReadTts.createSpeaker(fake.win);
  speaker.speak("あ", 1);
  assert.equal(speaker.pause(), true);
  assert.equal(fake.calls.pause, 1);
  assert.equal(speaker.resume(), true);
  assert.equal(fake.calls.resume, 1);
  speaker.stop();
  assert.equal(fake.calls.cancel, 1);
  // 无 pause 能力时返回 false 供上层降级。
  const poor = ReadTts.createSpeaker(fakeWin({ noPause: true }).win);
  poor.speak("あ", 1);
  assert.equal(poor.pause(), false);
});

test("不支持时全部操作安全降级", function () {
  const speaker = ReadTts.createSpeaker(fakeWin({ noSynth: true }).win);
  assert.equal(speaker.speak("あ", 1), false);
  assert.equal(speaker.pause(), false);
  assert.equal(speaker.resume(), false);
  speaker.stop();
  assert.equal(speaker.speaking(), false);
});
