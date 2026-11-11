// 语法整理引擎：跨词性的概念地图，纯数据 + 查询，不碰 DOM、不读写存储。
//
// 与练习型引擎的边界：这里的内容是「查阅型结论」，不生成可判定题目，
// 因此不接全局错题本、不计入每日打卡。能对应到既有题目的条目只挂跳转。
//
// 内容分三层，顺序即教学主线：词内变形 → 功能转换 → 句子打包。
// 新增主题时保持每层四块（结论 / 对照表 / 例句 / 易错点），由 validateTopics 守护。

(function (root) {
  "use strict";

  // 练一练只允许指向这些既有落点，避免拼出不存在的视图。
  var PRACTICE_TARGETS = [
    "verb-practice",
    "verb-reference",
    "adjective-reference",
    "particle-practice",
    "particle-learn"
  ];

  var TOPICS = [
    {
      id: "inflection",
      label: "词性变形总览",
      alias: "谁会变，谁不会变",
      eyebrow: "第一层 · 词内变形",
      lead: "只有用言（动词、い形容词、な形容词）会改变词形；名词、副词、助词、连体词不变形，靠加助词或助动词改变功能。",
      summary: "一句话总纲：会变的只有用言，其余靠「加东西」改功能。",
      table: {
        caption: "各词性的变形手段与主要形式",
        columns: ["词性", "是否活用", "变形靠什么", "主要形式"],
        rows: [
          ["动词", "有", "词尾变化（五段／一段／サ変／カ変）", "ます・て・た・ない・ば・命令・意向・可能・受身・使役"],
          ["い形容词", "有", "词尾「い」", "〜く／くない／かった／くて／ければ"],
          ["な形容词", "有", "词干＋だ系列", "〜に／〜な／だ／だった／ではない／なら"],
          ["名词", "无", "加助词、加 だ／です", "だ／です／だった／ではない／の"],
          ["副词・助词・连体词", "无", "完全不变", "直接修饰用言，或只表关系"],
          ["助动词", "部分有", "自身也活用", "だ・ます・た・ない・たい・ようだ・そうだ"]
        ]
      },
      examples: [
        {
          jp: "食べる → 食べた／食べたい／食べよう",
          kana: "たべる → たべた／たべたい／たべよう",
          zh: "后两个变的其实是助动词 たい／よう，动词词干没动。",
          note: "助动词是第二条变形线"
        },
        {
          jp: "高い → 高く／高くない／高かった",
          kana: "たかい → たかく／たかくない／たかかった",
          zh: "い形容词只换词尾，「高く」可直接修饰动词。",
          note: "い → く 是副词用法"
        },
        {
          jp: "静かだ → 静かに／静かな／静かだった",
          kana: "しずかだ → しずかに／しずかな／しずかだった",
          zh: "な形容词变的是后面的 だ 系列，词干不动。",
          note: "に 修饰动词，な 修饰名词"
        },
        {
          jp: "学生だ → 学生だった／学生ではない／学生の本",
          kana: "がくせいだ → がくせいだった／がくせいではない／がくせいのほん",
          zh: "名词本身不变，谓语化靠 だ，修饰名词靠 の。",
          note: "体言不活用"
        }
      ],
      pitfalls: [
        {
          wrong: "把「食べた」「食べたい」全算成动词变形",
          right: "只有 て／た／ます 等接在动词上；たい、ようだ、そうだ 是助动词自己活用",
          note: "漏掉助动词这一行，就看不懂为什么词干一直不变"
        },
        {
          wrong: "いい → いく／いかった",
          right: "いい → よく／よかった／よくない",
          note: "いい 的变形走「よ-」词干，是特例"
        },
        {
          wrong: "名词＋の＋动词",
          right: "名词＋の＋名词（学生の本）；修饰动词要换助词或后缀",
          note: "の 只能构成连体修饰"
        }
      ],
      practice: [
        { label: "练动词变形", target: "verb-practice" },
        { label: "查动词活用表", target: "verb-reference" },
        { label: "查形容词形式", target: "adjective-reference" }
      ]
    },
    {
      id: "noun-adverb",
      label: "名词怎么当副词",
      alias: "不变形，改功能",
      eyebrow: "第二层 · 功能转换",
      lead: "名词（体言）不活用。所谓「名词变副词」是个俗称，实际是三条路：本来就能作状语、加格助词构成连用修饰、加派生后缀造副词。",
      summary: "变形发生在助词和后缀上，不在名词上。",
      table: {
        caption: "名词取得副词性功能的三条路径",
        columns: ["路径", "形式", "例"],
        rows: [
          ["本来就能作状语", "时间・数量・程度名词，无标记", "明日行く／三時間待つ／少し休む／全部食べた"],
          ["加格助词", "に（时点・归着・结果）・で（手段・状态・范围）・と（引用・伴随）・から／まで／へ／より", "三時に会う／電車で行く／一人で住む／先生となる"],
          ["加派生后缀", "〜に／〜と／〜的に／〜のように", "静かに話す／はっきりと言う／具体的に説明する"]
        ]
      },
      examples: [
        {
          jp: "明日行く　／　三時に会う",
          kana: "あした いく　／　さんじに あう",
          zh: "时间名词本身可作状语；要标出「时点」才加 に。",
          note: "有标与无标"
        },
        {
          jp: "一人で住む　／　全力で走る",
          kana: "ひとりで すむ　／　ぜんりょくで はしる",
          zh: "で 表状态或方式，不是「用一个人」「用全力」这种工具义。",
          note: "で 的状态义"
        },
        {
          jp: "具体的に説明する",
          kana: "ぐたいてきに せつめいする",
          zh: "名词＋的 → な形容词 → に，绕了一圈才成副词。",
          note: "〜的に 是派生链"
        }
      ],
      pitfalls: [
        {
          wrong: "把「静かに」「具体的に」当成名词在变形",
          right: "那是な形容词词干＋に；「速く」是い形容词く形，两者都不是名词",
          note: "这是「名词变副词」最常见的术语陷阱"
        },
        {
          wrong: "静かの話す",
          right: "静かに話す（修饰动词）／静かな部屋（修饰名词）",
          note: "の 只做连体修饰，に／な 分工不同"
        },
        {
          wrong: "三時に　⇄　三時間で 随便互换",
          right: "三時に＝时点；三時間で＝期限或范围；三時間待つ＝数量状语不加助词",
          note: "に 与 で 的功能不能对调"
        }
      ],
      practice: [
        { label: "练助词 に／で", target: "particle-practice" },
        { label: "看助词用法", target: "particle-learn" },
        { label: "查形容词副词形", target: "adjective-reference" }
      ]
    },
    {
      id: "embedded-question",
      label: "疑问短句",
      alias: "句子打包成名词",
      eyebrow: "第三层 · 句子打包",
      lead: "把一个疑问句用简体＋か（或かどうか）打包成名词性成分，塞进大句子当主语或宾语。这是句子层面的名词化，跟词形变化无关。",
      summary: "判断标准：疑问句后面还挂着别的谓语，它就降格成了名词。",
      table: {
        caption: "疑问短句的构成规则",
        columns: ["类型", "规则", "例"],
        rows: [
          ["有疑问词", "简体句＋か", "何を食べるか まだ決めていない"],
          ["是非问（无疑问词）", "简体句＋かどうか", "田中さんが来るかどうか 分かりません"],
          ["前半句形式", "用简体（ますか → か）", "行きますか → 行くか決める"],
          ["常接谓语", "分かる・知る・決める・教える・聞く・考える・確認する", "どの辞書がいいか 教えてください"]
        ]
      },
      examples: [
        {
          jp: "何を食べますか。　→　何を食べるか まだ決めていません。",
          kana: "なにを たべますか　→　なにを たべるか まだ きめていません",
          zh: "左边在问别人；右边整块成了「決める」的宾语。",
          note: "独立问句 vs 嵌入句"
        },
        {
          jp: "田中さんが来るかどうか 分かりません。",
          kana: "たなかさんが くるかどうか わかりません",
          zh: "没有疑问词的是非问，必须用 かどうか。",
          note: "是非问的打包"
        },
        {
          jp: "走るの が好き　／　行くか 分からない",
          kana: "はしるのが すき　／　いくか わからない",
          zh: "か 与 の 都能把句子名词化，か 保留疑问义。",
          note: "名词化标记不止一个"
        }
      ],
      pitfalls: [
        {
          wrong: "何を食べるかどうか",
          right: "何を食べるか",
          note: "已有疑问词就不再加 どうか"
        },
        {
          wrong: "来るか分かりません",
          right: "来るかどうか分かりません",
          note: "口语能懂，书面规范要 かどうか"
        },
        {
          wrong: "把短句里的 が 一律读成「强调」",
          right: "どこがいいか 里的 が 标记疑问焦点，需结合疑问语境判断",
          note: "与助词模块的「が 标出新信息」是同一条规则"
        }
      ],
      practice: [
        { label: "看助词 が 的焦点", target: "particle-learn" },
        { label: "练助词选择", target: "particle-practice" }
      ]
    }
  ];

  var TOPIC_IDS = TOPICS.map(function (topic) {
    return topic.id;
  });

  function getTopic(id) {
    for (var i = 0; i < TOPICS.length; i += 1) {
      if (TOPICS[i].id === id) {
        return TOPICS[i];
      }
    }
    throw new RangeError("未知语法主题：" + id);
  }

  // 内容完整性自检：新增主题时漏掉某一块，或表格行列对不上，测试会拦住。
  function validateTopics() {
    var problems = [];
    var seen = Object.create(null);
    TOPICS.forEach(function (topic) {
      if (seen[topic.id]) {
        problems.push("主题 id 重复：" + topic.id);
      }
      seen[topic.id] = true;
      ["id", "label", "alias", "eyebrow", "lead", "summary"].forEach(function (key) {
        if (typeof topic[key] !== "string" || !topic[key]) {
          problems.push(topic.id + " 缺少 " + key);
        }
      });
      if (!topic.table || !topic.table.columns || !topic.table.rows) {
        problems.push(topic.id + " 缺少对照表");
      } else {
        var width = topic.table.columns.length;
        if (width < 2) {
          problems.push(topic.id + " 对照表列数不足");
        }
        topic.table.rows.forEach(function (row, index) {
          if (!Array.isArray(row) || row.length !== width) {
            problems.push(topic.id + " 对照表第 " + (index + 1) + " 行列数不符");
          }
        });
        if (topic.table.rows.length < 3) {
          problems.push(topic.id + " 对照表行数不足");
        }
      }
      [["examples", "jp"], ["pitfalls", "wrong"], ["practice", "label"]].forEach(function (pair) {
        var list = topic[pair[0]];
        if (!Array.isArray(list) || list.length === 0) {
          problems.push(topic.id + " 缺少 " + pair[0]);
          return;
        }
        list.forEach(function (entry) {
          if (typeof entry[pair[1]] !== "string" || !entry[pair[1]]) {
            problems.push(topic.id + " 的 " + pair[0] + " 项缺 " + pair[1]);
          }
        });
      });
      (topic.practice || []).forEach(function (link) {
        if (PRACTICE_TARGETS.indexOf(link.target) === -1) {
          problems.push(topic.id + " 的练一练落点无效：" + link.target);
        }
      });
    });
    return problems;
  }

  var api = {
    TOPICS: TOPICS,
    TOPIC_IDS: TOPIC_IDS,
    PRACTICE_TARGETS: PRACTICE_TARGETS,
    getTopic: getTopic,
    validateTopics: validateTopics
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  root.JapaneseGrammarEngine = api;
})(globalThis);
