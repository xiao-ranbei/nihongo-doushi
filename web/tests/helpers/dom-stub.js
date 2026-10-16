"use strict";

// 最小 DOM 桩：让 app.js 能在 node --test 下原样加载，零新增依赖。
//
// 设计要点：元素树由 index.html 真实解析而来，而不是手写一份影子 DOM。
// 这样 app.js 里任何 getElementById("x") 一旦指向 index.html 中不存在的 id，
// 测试立刻失败 —— 这正是纯静态页面最容易出、又最难在浏览器之外发现的 bug。
//
// 只实现 app.js 实际用到的 API；新增用到的 DOM 能力时在此补齐即可。

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const PROJECT_ROOT = path.join(__dirname, "..", "..");
const VOID_TAGS = new Set([
  "area", "base", "br", "col", "embed", "hr", "img",
  "input", "link", "meta", "param", "source", "track", "wbr"
]);
const BOOLEAN_ATTRS = new Set(["hidden", "disabled", "required", "checked", "selected"]);

function toCamelCase(name) {
  return name.replace(/-([a-z])/g, function (match, letter) {
    return letter.toUpperCase();
  });
}

function matches(element, selector) {
  const rule = String(selector).trim();
  if (rule.charAt(0) === ".") {
    return element._classes.has(rule.slice(1));
  }
  if (rule.charAt(0) === "#") {
    return element.id === rule.slice(1);
  }
  const attribute = /^\[([a-zA-Z-]+)\]$/.exec(rule);
  if (attribute) {
    return Object.prototype.hasOwnProperty.call(element.attributes, attribute[1]);
  }
  return element.tagName === rule.toUpperCase();
}

function walk(node, visit) {
  visit(node);
  node.childNodes.forEach(function (child) {
    if (child.nodeType !== 3) {
      walk(child, visit);
    }
  });
}

function noop() {}

function createTextNode(text) {
  return { nodeType: 3, textContent: String(text), parentNode: null };
}

function detachAll(element) {
  element.childNodes.forEach(function (child) {
    child.parentNode = null;
  });
  element.childNodes.length = 0;
}

function applyAttribute(element, name, value) {
  if (name === "id") {
    element.id = value;
  } else if (name === "class") {
    element.className = value;
  } else if (name === "value") {
    element.value = value;
  } else if (name === "title") {
    element.title = value;
  } else if (name === "type") {
    element.type = value;
  } else if (name.indexOf("data-") === 0) {
    element.dataset[toCamelCase(name.slice(5))] = value;
  } else if (BOOLEAN_ATTRS.has(name)) {
    element[name] = true;
  }
}

function createElement(tag) {
  const classes = new Set();
  const element = {
    tagName: String(tag).toUpperCase(),
    nodeType: 1,
    childNodes: [],
    parentNode: null,
    ownerDocument: null,
    attributes: {},
    dataset: {},
    style: {},
    id: "",
    title: "",
    value: "",
    type: "",
    hidden: false,
    disabled: false,
    required: false,
    focused: false,
    offsetWidth: 0,
    innerHTML: "",
    _text: "",
    _classes: classes,
    _listeners: {}
  };

  Object.defineProperty(element, "className", {
    enumerable: true,
    configurable: true,
    get: function () {
      return Array.from(classes).join(" ");
    },
    set: function (value) {
      classes.clear();
      String(value).split(/\s+/).forEach(function (name) {
        if (name) {
          classes.add(name);
        }
      });
    }
  });

  Object.defineProperty(element, "textContent", {
    enumerable: true,
    configurable: true,
    get: function () {
      if (element.childNodes.length) {
        return element.childNodes.map(function (node) {
          return node.textContent;
        }).join("");
      }
      return element._text;
    },
    set: function (value) {
      detachAll(element);
      element._text = String(value);
    }
  });

  element.classList = {
    add: function () {
      Array.prototype.slice.call(arguments).forEach(function (name) {
        classes.add(name);
      });
    },
    remove: function () {
      Array.prototype.slice.call(arguments).forEach(function (name) {
        classes.delete(name);
      });
    },
    contains: function (name) {
      return classes.has(name);
    },
    toggle: function (name, force) {
      const on = force === undefined ? !classes.has(name) : Boolean(force);
      if (on) {
        classes.add(name);
      } else {
        classes.delete(name);
      }
      return on;
    }
  };

  element.setAttribute = function (name, value) {
    const text = String(value);
    element.attributes[name] = text;
    applyAttribute(element, name, text);
  };
  element.getAttribute = function (name) {
    return Object.prototype.hasOwnProperty.call(element.attributes, name)
      ? element.attributes[name]
      : null;
  };
  element.removeAttribute = function (name) {
    delete element.attributes[name];
  };
  element.addEventListener = function (type, handler) {
    if (!element._listeners[type]) {
      element._listeners[type] = [];
    }
    element._listeners[type].push(handler);
  };
  element.append = function () {
    Array.prototype.slice.call(arguments).forEach(function (child) {
      const node = typeof child === "string" ? createTextNode(child) : child;
      node.parentNode = element;
      element.childNodes.push(node);
    });
  };
  element.replaceChildren = function () {
    detachAll(element);
    element._text = "";
    if (element.tagName === "SELECT") {
      element.value = "";
    }
    element.append.apply(element, arguments);
  };
  element.remove = function () {
    if (!element.parentNode) {
      return;
    }
    const siblings = element.parentNode.childNodes;
    siblings.splice(siblings.indexOf(element), 1);
    element.parentNode = null;
  };
  element.focus = function () {
    element.focused = true;
    if (element.ownerDocument) {
      element.ownerDocument.activeElement = element;
    }
  };
  element.reportValidity = function () {
    return Boolean(element.value);
  };
  element.closest = function (selector) {
    let node = element;
    while (node) {
      if (matches(node, selector)) {
        return node;
      }
      node = node.parentNode;
    }
    return null;
  };
  element.querySelector = function (selector) {
    return element.querySelectorAll(selector)[0] || null;
  };
  element.querySelectorAll = function (selector) {
    const found = [];
    walk(element, function (node) {
      if (node !== element && node.nodeType !== 3 && matches(node, selector)) {
        found.push(node);
      }
    });
    return found;
  };
  // 测试驱动：同步触发监听器，不做冒泡（app.js 目前没有依赖冒泡的委托）。
  element.dispatch = function (type, extra) {
    const event = Object.assign({
      type: type,
      target: element,
      isComposing: false,
      defaultPrevented: false,
      preventDefault: function () {
        event.defaultPrevented = true;
      },
      stopPropagation: noop
    }, extra || {});
    (element._listeners[type] || []).slice().forEach(function (handler) {
      handler.call(element, event);
    });
    return event;
  };
  // 真实 DOM 的程序化点击（如导出时临时 anchor.click()）。
  element.click = function () {
    element.dispatch("click");
  };

  // <select> 补齐选项后浏览器会自动选中第一项，桩需跟随，否则筛选初值为空串。
  if (element.tagName === "SELECT") {
    const nativeAppend = element.append;
    element.append = function () {
      nativeAppend.apply(element, arguments);
      const firstOption = element.childNodes.filter(function (node) {
        return node.tagName === "OPTION";
      })[0];
      if (firstOption && !element.value) {
        element.value = firstOption.value;
      }
    };
  }

  return element;
}

// 只解析标签与属性的轻量 HTML 扫描器：够还原 id / class / data-* / 布尔属性与层级。
function parseHtml(html) {
  const root = createElement("#root");
  const stack = [root];
  const pattern = /<!--[\s\S]*?-->|<!doctype[^>]*>|<\/([a-zA-Z][\w-]*)\s*>|<([a-zA-Z][\w-]*)((?:[^>"']|"[^"]*"|'[^']*')*?)\/?>/gi;
  let match = pattern.exec(html);

  while (match) {
    const closingTag = match[1];
    const openingTag = match[2];
    if (closingTag) {
      for (let index = stack.length - 1; index > 0; index -= 1) {
        if (stack[index].tagName === closingTag.toUpperCase()) {
          stack.length = index;
          break;
        }
      }
    } else if (openingTag) {
      const element = createElement(openingTag);
      const attributeText = match[3] || "";
      const attributePattern = /([:\w-]+)(?:\s*=\s*"([^"]*)")?/g;
      let attribute = attributePattern.exec(attributeText);
      while (attribute) {
        const name = attribute[1];
        const value = attribute[2] === undefined ? "" : attribute[2];
        element.attributes[name] = value;
        applyAttribute(element, name, value);
        attribute = attributePattern.exec(attributeText);
      }
      stack[stack.length - 1].append(element);
      if (!VOID_TAGS.has(openingTag.toLowerCase())) {
        stack.push(element);
      }
    }
    match = pattern.exec(html);
  }

  return root;
}

function findByTag(root, tag) {
  let found = null;
  walk(root, function (node) {
    if (!found && node.tagName === tag) {
      found = node;
    }
  });
  return found;
}

function createDocument(root) {
  const documentElement = findByTag(root, "HTML") || root;
  const byId = new Map();
  walk(documentElement, function (node) {
    node.ownerDocument = null;
    if (node.id) {
      byId.set(node.id, node);
    }
  });

  const document = {
    documentElement: documentElement,
    head: findByTag(documentElement, "HEAD") || createElement("head"),
    body: findByTag(documentElement, "BODY") || createElement("body"),
    baseURI: "file:///" + path.basename(PROJECT_ROOT) + "/index.html",
    currentScript: null,
    activeElement: null,
    createElement: createElement,
    createTextNode: createTextNode,
    getElementById: function (id) {
      return byId.get(id) || null;
    },
    querySelector: function (selector) {
      return document.querySelectorAll(selector)[0] || null;
    },
    querySelectorAll: function (selector) {
      const found = [];
      walk(documentElement, function (node) {
        if (node.nodeType !== 3 && matches(node, selector)) {
          found.push(node);
        }
      });
      return found;
    }
  };

  walk(documentElement, function (node) {
    node.ownerDocument = document;
  });
  return document;
}

function createWindow() {
  const html = fs.readFileSync(path.join(PROJECT_ROOT, "index.html"), "utf8");
  const document = createDocument(parseHtml(html));
  const store = new Map();
  const timers = [];
  // 导出下载桩：记录每次 createObjectURL 的 blob，供测试断言导出内容。
  const downloads = [];

  return {
    document: document,
    timers: timers,
    __mistakeDownloads: downloads,
    localStorage: {
      getItem: function (key) {
        return store.has(String(key)) ? store.get(String(key)) : null;
      },
      setItem: function (key, value) {
        store.set(String(key), String(value));
      },
      removeItem: function (key) {
        store.delete(String(key));
      },
      clear: function () {
        store.clear();
      }
    },
    setTimeout: function (handler) {
      timers.push(handler);
      return timers.length;
    },
    clearTimeout: function () {},
    confirm: function () {
      return true;
    },
    Blob: function (parts, options) {
      this._text = parts.map(String).join("");
      this.type = options && options.type;
    },
    URL: {
      createObjectURL: function (blob) {
        const url = "blob:stub-" + (downloads.length + 1);
        downloads.push({ url: url, blob: blob });
        return url;
      },
      revokeObjectURL: function () {}
    },
    // FileReader 同步触发 onload：导入测试直接构造 { _content } 伪 File。
    FileReader: function () {
      this.readAsText = function (file) {
        this.result = file && file._content !== undefined ? String(file._content) : "";
        if (typeof this.onload === "function") {
          this.onload({ target: this });
        }
      };
      this.onerror = null;
    },
    JapaneseVerbEngine: require(path.join(PROJECT_ROOT, "verb-engine.js")),
    JapaneseAdjectiveEngine: require(path.join(PROJECT_ROOT, "adjective-engine.js")),
    JapaneseParticleEngine: require(path.join(PROJECT_ROOT, "particle-engine.js")),
    JapaneseParticleEngine: require(path.join(PROJECT_ROOT, "particle-engine.js")),
    // 与 font/fonts.js 产出的结构一致，用于覆盖动态 @font-face 注入路径。
    JapaneseFontManifest: [
      {
        family: "LXGW ZhenKai GB",
        src: "font/LXGWZhenKaiGB-Regular.subset.woff2",
        format: "woff2",
        weight: "400",
        style: "normal",
        display: "swap"
      }
    ]
  };
}

function loadApp(window) {
  const source = fs.readFileSync(path.join(PROJECT_ROOT, "app.js"), "utf8");
  const sandbox = { window: window, document: window.document, console: console };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: "app.js" });
  return window.document;
}

// options.storage 可在加载 app.js 前预置 localStorage，用于验证存档的防御性校验。
function createApp(options) {
  const settings = options || {};
  const window = createWindow();
  Object.keys(settings.storage || {}).forEach(function (key) {
    window.localStorage.setItem(key, settings.storage[key]);
  });
  const document = loadApp(window);
  const input = document.getElementById("answer-input");
  const form = document.getElementById("answer-form");

  return {
    window: window,
    document: document,
    $: function (id) {
      const element = document.getElementById(id);
      if (!element) {
        throw new Error("index.html 中找不到 #" + id);
      }
      return element;
    },
    click: function (element) {
      return element.dispatch("click");
    },
    // 模拟一次作答；extra 可注入 isComposing 等事件字段。
    answer: function (value, extra) {
      input.value = value;
      form.dispatch("submit", extra);
    }
  };
}

module.exports = {
  createApp: createApp,
  createWindow: createWindow,
  createElement: createElement,
  parseHtml: parseHtml
};
