"use strict";

// 跨端一致性样例生成脚本（Node 零依赖）。
// 作用：以 web/verb-engine.js 与 web/adjective-engine.js 的实际输出为期望值，
// 穷举生成跨端样例集，供 Android 端 Kotlin 引擎测试比对：
//   - 动词：词条 × 语态 × 活用形式 的 { written, kana } 与完整规则说明；
//   - 形容词：词条 × 形式 的 { written, kana, variants } 与说明；
//   - 答案归一化：汉字 / 假名 / 片假名 / 全半角 / 长音符 / 罗马字等代表性输入。
// 样例总量控制在 5000 条以内；动词条目超出预算时按词条均匀抽样（首尾必含）。
//
// 用法：
//   node web/tools/generate-cross-samples.js
// 输出：
//   android/app/src/test/resources/cross-samples.json
//   顶层结构 { generatedAt, verb: [...], adjective: [...], normalization: [...] }

const fs = require("node:fs");
const path = require("node:path");

const verbEngine = require("../verb-engine.js");
const adjectiveEngine = require("../adjective-engine.js");

const TARGET_MAX = 5000;

// 在 items 中均匀抽取 count 个（保留首尾，索引确定，无随机数）。
// 用 floor 取整：相对 round 能让 44 词条取 38 个时覆盖 suru（する）本身，
// 且仍覆盖全部五段词尾与 kuru。
function evenSample(items, count) {
  if (count >= items.length) {
    return items.slice();
  }
  if (count <= 0) {
    return [];
  }
  const picked = [];
  for (let i = 0; i < count; i += 1) {
    const index = Math.floor((i * (items.length - 1)) / (count - 1));
    picked.push(items[index]);
  }
  return picked;
}

function buildVerbSamples() {
  const perVerb = verbEngine.VOICES.length * verbEngine.INFLECTIONS.length;
  const budget = TARGET_MAX - adjectiveEngine.ADJECTIVES.length * adjectiveEngine.FORMS.length - NORMALIZATION_INPUTS.length;
  const maxVerbs = Math.floor(budget / perVerb);
  const sampledVerbs = evenSample(verbEngine.VERBS, maxVerbs);

  const samples = [];
  for (const verb of sampledVerbs) {
    for (const voice of verbEngine.VOICES) {
      for (const inflection of verbEngine.INFLECTIONS) {
        const result = verbEngine.conjugate(verb, voice.id, inflection.id);
        samples.push({
          verbId: verb.id,
          voiceId: voice.id,
          inflectionId: inflection.id,
          expected: {
            written: result.written,
            kana: result.kana,
            explain: verbEngine.explain(verb, voice.id, inflection.id),
          },
        });
      }
    }
  }
  return samples;
}

function buildAdjectiveSamples() {
  const samples = [];
  for (const adjective of adjectiveEngine.ADJECTIVES) {
    for (const form of adjectiveEngine.FORMS) {
      const result = adjectiveEngine.conjugate(adjective, form.id);
      samples.push({
        adjectiveId: adjective.id,
        formId: form.id,
        expected: {
          written: result.written,
          kana: result.kana,
          variants: adjectiveEngine.answerVariants(result),
          explain: adjectiveEngine.explain(adjective, form.id),
        },
      });
    }
  }
  return samples;
}

// 答案归一化代表性输入：覆盖汉字直通、平假名、片假名、半角片假名、全角字母、
// 中英标点、长音符展开、罗马字（含拨音 nn、促音重复、撇号、无法映射回退）与空串。
const NORMALIZATION_INPUTS = [
  "かきます。",
  "、かきます",
  "かきます」",
  "「かきます」",
  "カキマス",
  "ｶｷﾏｽ",
  "kakimasu.",
  "kakimasu!",
  "tōkyō",
  "okāsan",
  "きてー",
  "ｔａｂｅｍａｓｕ",
  "  ka ki masu  ",
  "",
  "。。。",
  "勉強します",
  "行く",
  "たかなかった",
  "takanakatta",
  "shizukajyanai",
  "katta",
  "konbanwa",
  "rinngo",
  "shimbun",
  "n'",
  "hello",
  "xyz",
  "ジツ",
  "キョウト",
  "ノート",
];

function buildNormalizationSamples() {
  return NORMALIZATION_INPUTS.map(function (input) {
    return {
      input: input,
      expected: verbEngine.normalizeAnswer(input),
    };
  });
}

function main() {
  const normalization = buildNormalizationSamples();
  const verb = buildVerbSamples();
  const adjective = buildAdjectiveSamples();

  const total = verb.length + adjective.length + normalization.length;
  if (total > TARGET_MAX) {
    throw new Error("样例总量 " + total + " 超出预算 " + TARGET_MAX);
  }

  // meta 由生成器按当前引擎数据计算，测试据此做自一致断言：
  // 数据扩容后条数自动随源变化，不存在硬编码条数漂移（IK-03）。
  const payload = {
    generatedAt: new Date().toISOString(),
    meta: {
      verbSampleCount: verb.length,
      adjectiveSampleCount: adjective.length,
      normalizationSampleCount: normalization.length,
      sampledVerbIds: verb
        .map(function (sample) { return sample.verbId; })
        .filter(function (id, index, all) { return all.indexOf(id) === index; }),
    },
    verb: verb,
    adjective: adjective,
    normalization: normalization,
  };

  const outputDir = path.join(__dirname, "..", "..", "android", "app", "src", "test", "resources");
  fs.mkdirSync(outputDir, { recursive: true });
  const outputPath = path.join(outputDir, "cross-samples.json");
  fs.writeFileSync(outputPath, JSON.stringify(payload), "utf8");

  process.stdout.write(
    "generated " + outputPath + "\n" +
    "verb: " + verb.length + " entries\n" +
    "adjective: " + adjective.length + " entries\n" +
    "normalization: " + normalization.length + " entries\n" +
    "total: " + total + "\n"
  );
}

main();
