"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const engine = require("../particle-engine.js");

test("助词学习内容覆盖入门与进阶对比", function () {
  const lessonIds = new Set(engine.LESSONS.map(function (lesson) {
    return lesson.id;
  }));
  assert.ok(engine.LESSONS.length >= 8, "应覆盖常见助词功能组");
  assert.deepEqual(
    engine.LEVELS.map(function (level) { return level.id; }),
    ["beginner", "advanced"]
  );

  engine.LESSONS.forEach(function (lesson) {
    assert.ok(lesson.particles.length > 0, lesson.id + " 应标明助词");
    assert.ok(lesson.examples.length > 0, lesson.id + " 应提供语境例句");
    engine.LEVELS.forEach(function (level) {
      assert.ok(
        engine.getQuestions(level.id, lesson.id).length > 0,
        lesson.id + " 应有" + level.label + "练习"
      );
    });
  });

  assert.equal(lessonIds.size, engine.LESSONS.length, "专题 ID 应唯一");
});

test("助词题选项、标准答案和反馈资料完整", function () {
  const questionIds = new Set();
  engine.QUESTIONS.forEach(function (question) {
    assert.equal(questionIds.has(question.id), false, "题目 ID 应唯一：" + question.id);
    questionIds.add(question.id);
    assert.ok(engine.getLesson(question.lessonId), question.id + " 应关联专题");
    assert.ok(question.context, question.id + " 应提供语境");
    assert.ok(question.before || question.after, question.id + " 应有句子内容");
    assert.ok(question.options.length >= 3, question.id + " 应有足够的选择项");
    assert.ok(question.acceptedAnswers.length > 0, question.id + " 应有可接受答案");
    assert.ok(question.beginnerExplanation, question.id + " 应有入门解释");
    assert.ok(question.advancedExplanation, question.id + " 应有进阶解释");
    question.acceptedAnswers.forEach(function (answer) {
      assert.ok(question.options.includes(answer), question.id + " 的答案必须在选项中");
    });
  });
});

test("助词作用判断题定位目标助词且支持综合交替练习", function () {
  engine.LEVELS.forEach(function (level) {
    engine.LESSONS.forEach(function (lesson) {
      const fillQuestions = engine.getQuestions(level.id, lesson.id, "fill");
      const functionQuestions = engine.getQuestions(level.id, lesson.id, "function");
      assert.ok(fillQuestions.length > 0, lesson.id + " 应有选助词题");
      assert.equal(
        functionQuestions.length,
        fillQuestions.length,
        lesson.id + " 的两类题量应平衡，确保每轮严格交替"
      );
      functionQuestions.forEach(function (question) {
        assert.ok(question.targetParticle, question.id + " 应明确目标助词");
        assert.ok(question.targetReading, question.id + " 应提供目标助词读音");
        assert.equal(question.options.length, 4, question.id + " 应提供四个判断选项");
        assert.equal(new Set(question.options).size, 4, question.id + " 的选项应互不重复");
        assert.equal(question.acceptedAnswers.length, 1, question.id + " 应只有一个明确的正确解释");
      });
    });
  });

  const weatherQuestion = engine.getQuestion("function-weather-ga");
  assert.equal(
    weatherQuestion.before + weatherQuestion.targetParticle + weatherQuestion.after,
    "天気がいいから、散歩しましょう。"
  );
  assert.equal(engine.checkAnswer(weatherQuestion, weatherQuestion.acceptedAnswers[0]), true);
  assert.equal(engine.checkAnswer(weatherQuestion, weatherQuestion.options[1]), false);
});

test("判题接受题目声明的多个自然答案并规范空白", function () {
  const directionQuestion = engine.getQuestion("he-destination");
  assert.equal(engine.checkAnswer(directionQuestion, "へ"), true);
  assert.equal(engine.checkAnswer(directionQuestion, " に "), true);
  assert.equal(engine.checkAnswer(directionQuestion, "で"), false);
  assert.equal(engine.checkAnswer("shika-negative", "しか"), true);
  assert.throws(function () {
    engine.checkAnswer("missing-question", "に");
  }, TypeError);
  assert.throws(function () {
    engine.getQuestions("unknown");
  }, RangeError);
  assert.throws(function () {
    engine.getQuestions("beginner", undefined, "unknown");
  }, RangeError);
});

test("每个例句都有最小对立字段，且与所属专题一致", function () {
  engine.LESSONS.forEach(function (lesson) {
    lesson.examples.forEach(function (example, index) {
      const label = lesson.id + " 例句 " + (index + 1);
      assert.equal(example.contrastGroup, lesson.id, label + " 的分组应与专题一致");
      assert.ok(example.contrastNote && example.contrastNote.length >= 8, label + " 缺对比说明");
    });
  });
});

test("选助词题的逐项说明覆盖干扰项且不指向正确答案", function () {
  engine.QUESTIONS.filter(function (question) {
    return question.type === "fill";
  }).forEach(function (question) {
    const notes = question.optionNotes;
    assert.ok(notes && Object.keys(notes).length > 0, question.id + " 缺 optionNotes");
    Object.keys(notes).forEach(function (option) {
      assert.ok(question.options.indexOf(option) >= 0, question.id + " 的说明键不在选项内：" + option);
      assert.ok(question.acceptedAnswers.indexOf(option) < 0, question.id + " 不该给正确答案写说明：" + option);
      assert.ok(notes[option].length >= 8, question.id + " 的说明过短：" + option);
    });
  });
});
