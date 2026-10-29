(() => {
  // src/settings/defaults.js
  var FREE_PRIVACY_SETTING_KEYS = Object.freeze([
    "igTyping",
    "igSeen",
    "igStory",
    "msgTyping",
    "msgSeen",
    "msgStory"
  ]);
  var DEFAULT_PRIVACY_SETTINGS = Object.freeze({
    igTyping: true,
    igSeen: true,
    igStory: true,
    msgTyping: true,
    msgSeen: true,
    msgStory: true
  });
  var DEFAULT_MODULE_FLAGS = Object.freeze({
    ghostMode: true
  });

  // src/config.js
  var SETTINGS = { ...DEFAULT_PRIVACY_SETTINGS };
  var KILLED_FEATURES = /* @__PURE__ */ new Set();
  var SETTINGS_READY = false;
  var hostname = window.location.hostname.toLowerCase();
  var pathname = window.location.pathname.toLowerCase();
  function isHost(domain) {
    return hostname === domain || hostname.endsWith(`.${domain}`);
  }
  var isFacebookDotCom = isHost("facebook.com");
  var isMessengerDotCom = isHost("messenger.com");
  var isFacebookMessengerProxy = hostname === "www.fbsbx.com" && pathname.startsWith("/maw_proxy_page");
  var isMessenger = isFacebookDotCom || isMessengerDotCom || isFacebookMessengerProxy;
  var isInstagram = isHost("instagram.com");
  function isKilled(feature) {
    return KILLED_FEATURES.has(feature);
  }
  function markSettingsReady() {
    SETTINGS_READY = true;
  }

  // src/utils/binary-json.js
  var MAX_STRUCTURED_BINARY_JSON_BYTES = 1024 * 1024;
  var FACEBOOK_TASK_FRAME_PREFIXES = [
    "{}\r\0",
    "g\0\0\0{}\rg\0\0"
  ];
  function sanitizeWholeJsonArrayBinary(value, sanitizer) {
    try {
      const input = getSupportedBytes(value);
      const source = input == null ? void 0 : input.bytes;
      if (!source || source.byteLength === 0 || source.byteLength > MAX_STRUCTURED_BINARY_JSON_BYTES) {
        return unchanged(value);
      }
      const first = firstNonWhitespaceByte(source);
      if (first !== 91 && first !== 123) return unchanged(value);
      const decoder = new TextDecoder("utf-8", { fatal: true });
      const encoder = new TextEncoder();
      const text = decoder.decode(source);
      const roundTrip = encoder.encode(text);
      if (!bytesEqual(source, roundTrip)) return unchanged(value);
      const sanitized = sanitizeJsonTaskBatchStringSource(text, sanitizer);
      if (!sanitized.changed || sanitized.blockedAll || typeof sanitized.value !== "string") {
        return unchanged(value, sanitized.blockedAll);
      }
      return changedBinary(input, encoder.encode(sanitized.value));
    } catch (e) {
      return unchanged(value);
    }
  }
  function sanitizeJsonTaskBatchStringSource(value, sanitizer) {
    try {
      const source = String(value || "");
      const leadingLength = source.length - source.trimStart().length;
      const trailingLength = source.length - source.trimEnd().length;
      const containerEnd = source.length - trailingLength;
      const containerSource = source.slice(leadingLength, containerEnd);
      if (!containerSource || containerSource[0] !== "[" && containerSource[0] !== "{") {
        return unchanged(value);
      }
      const sanitized = sanitizeJsonContainerSource(
        containerSource,
        sanitizer,
        0
      );
      if (!(sanitized == null ? void 0 : sanitized.changed)) return unchanged(value, sanitized == null ? void 0 : sanitized.blockedAll);
      if (sanitized.blockedAll)
        return { value: void 0, changed: true, blockedAll: true };
      return {
        value: `${source.slice(0, leadingLength)}${sanitized.source}${source.slice(containerEnd)}`,
        changed: true,
        blockedAll: false
      };
    } catch (e) {
      return unchanged(value);
    }
  }
  function sanitizeJsonContainerSource(source, sanitizer, depth) {
    if (depth > 8) return null;
    const parsed = JSON.parse(source);
    const sanitized = sanitizer(parsed, 0);
    if (!(sanitized == null ? void 0 : sanitized.changed))
      return { source, changed: false, blockedAll: false };
    if (sanitized.blockedAll) {
      return { source: void 0, changed: true, blockedAll: true };
    }
    const nextSource = rewriteJsonValueSource(
      source,
      parsed,
      sanitized.value,
      depth + 1
    );
    if (typeof nextSource !== "string") return null;
    return { source: nextSource, changed: true, blockedAll: false };
  }
  function rewriteJsonValueSource(source, original, sanitized, depth) {
    if (depth > 10) return null;
    if (jsonValuesEqual(original, sanitized)) return source;
    if (Array.isArray(original) && Array.isArray(sanitized)) {
      const rawItems = splitJsonArrayElements(source);
      if (!rawItems || rawItems.length !== original.length) return null;
      const rewritten = [];
      let originalIndex = 0;
      for (const nextItem of sanitized) {
        let matchingIndex = -1;
        for (let index = originalIndex; index < original.length; index += 1) {
          if (jsonValuesEqual(original[index], nextItem)) {
            matchingIndex = index;
            break;
          }
        }
        if (matchingIndex >= 0) {
          rewritten.push(rawItems[matchingIndex]);
          originalIndex = matchingIndex + 1;
          continue;
        }
        if (originalIndex >= original.length) {
          rewritten.push(JSON.stringify(nextItem));
          continue;
        }
        const rewrittenItem = rewriteJsonValueSource(
          rawItems[originalIndex],
          original[originalIndex],
          nextItem,
          depth + 1
        );
        if (typeof rewrittenItem !== "string") return null;
        rewritten.push(rewrittenItem);
        originalIndex += 1;
      }
      return `[${rewritten.join(",")}]`;
    }
    if (isPlainJsonObject(original) && isPlainJsonObject(sanitized)) {
      const properties = splitJsonObjectProperties(source);
      if (!properties) return null;
      const originalKeys = new Set(
        properties.map((property) => property.key)
      );
      if (originalKeys.size !== properties.length) return null;
      if (Object.keys(sanitized).some((key) => !originalKeys.has(key)))
        return null;
      const rewritten = [];
      for (const property of properties) {
        if (!Object.prototype.hasOwnProperty.call(sanitized, property.key))
          continue;
        const rewrittenValue = rewriteJsonValueSource(
          property.valueSource,
          original[property.key],
          sanitized[property.key],
          depth + 1
        );
        if (typeof rewrittenValue !== "string") return null;
        rewritten.push(`${property.keySource}:${rewrittenValue}`);
      }
      return `{${rewritten.join(",")}}`;
    }
    const replacement = JSON.stringify(sanitized);
    return typeof replacement === "string" ? replacement : null;
  }
  function splitJsonObjectProperties(source) {
    let index = skipWhitespace(source, 0);
    if (source[index] !== "{") return null;
    index += 1;
    const properties = [];
    while (index < source.length) {
      index = skipWhitespace(source, index);
      if (source[index] === "}") return properties;
      if (source[index] !== '"') return null;
      const keyStart = index;
      const keyEnd = scanJsonStringEnd(source, keyStart);
      if (keyEnd < 0) return null;
      const keySource = source.slice(keyStart, keyEnd);
      const key = JSON.parse(keySource);
      index = skipWhitespace(source, keyEnd);
      if (source[index] !== ":") return null;
      index = skipWhitespace(source, index + 1);
      const valueStart = index;
      const valueEnd = scanJsonValueEnd(source, valueStart);
      if (valueEnd < 0) return null;
      properties.push({
        key,
        keySource,
        valueSource: source.slice(valueStart, valueEnd)
      });
      index = skipWhitespace(source, valueEnd);
      if (source[index] === ",") {
        index += 1;
        continue;
      }
      if (source[index] === "}") return properties;
      return null;
    }
    return null;
  }
  function isPlainJsonObject(value) {
    return !!value && typeof value === "object" && !Array.isArray(value);
  }
  function jsonValuesEqual(left, right) {
    if (left === right) return true;
    try {
      return JSON.stringify(left) === JSON.stringify(right);
    } catch (e) {
      return false;
    }
  }
  function sanitizeFramedJsonTaskBatchBinary(value, sanitizer) {
    try {
      const input = getSupportedBytes(value);
      const source = input == null ? void 0 : input.bytes;
      if (!source || source.byteLength === 0 || source.byteLength > MAX_STRUCTURED_BINARY_JSON_BYTES) {
        return unchanged(value);
      }
      const decoder = new TextDecoder("utf-8", { fatal: true });
      const encoder = new TextEncoder();
      const text = decoder.decode(source);
      if (!bytesEqual(source, encoder.encode(text))) return unchanged(value);
      const framed = findFramedTaskEnvelopeSource(text);
      if (!framed) return unchanged(value);
      const retainedTasks = [];
      let changed = false;
      for (const rawTask of framed.rawTasks) {
        const task = JSON.parse(rawTask);
        const sanitized = sanitizer([task], 1);
        if (!(sanitized == null ? void 0 : sanitized.changed)) {
          retainedTasks.push(rawTask);
          continue;
        }
        changed = true;
        if (sanitized.blockedAll) {
          if (!Array.isArray(sanitized.value) || sanitized.value.length !== 0) {
            return unchanged(value);
          }
          continue;
        }
        if (!Array.isArray(sanitized.value) || sanitized.value.length !== 1) {
          return unchanged(value);
        }
        const rewrittenTask = rewriteJsonValueSource(
          rawTask,
          task,
          sanitized.value[0],
          0
        );
        if (typeof rewrittenTask !== "string") return unchanged(value);
        retainedTasks.push(rewrittenTask);
      }
      if (!changed || retainedTasks.length === 0)
        return unchanged(value, changed);
      const nextTasksSource = `[${retainedTasks.join(",")}]`;
      const nextInnerSource = replaceSpan(
        framed.innerSource,
        framed.tasksSpan,
        nextTasksSource
      );
      const nextOuterSource = replaceSpan(
        framed.outerSource,
        framed.payloadSpan,
        JSON.stringify(nextInnerSource)
      );
      const encoded = encoder.encode(
        `${framed.prefix}${nextOuterSource}${framed.trailingWhitespace}`
      );
      return changedBinary(input, encoded);
    } catch (e) {
      return unchanged(value);
    }
  }
  function findFramedTaskEnvelopeSource(text) {
    for (const prefix of FACEBOOK_TASK_FRAME_PREFIXES) {
      if (!text.startsWith(prefix)) continue;
      const candidate = text.slice(prefix.length);
      const outerSource = candidate.trimEnd();
      const trailingWhitespace = candidate.slice(outerSource.length);
      JSON.parse(outerSource);
      const payloadSpan = findTopLevelPropertyValueSpan(
        outerSource,
        "payload"
      );
      if (!payloadSpan || outerSource[payloadSpan.start] !== '"') continue;
      const rawPayloadToken = outerSource.slice(
        payloadSpan.start,
        payloadSpan.end
      );
      const innerSource = JSON.parse(rawPayloadToken);
      if (typeof innerSource !== "string") continue;
      if (rawPayloadToken !== JSON.stringify(innerSource)) continue;
      JSON.parse(innerSource);
      const tasksSpan = findTopLevelPropertyValueSpan(innerSource, "tasks");
      if (!tasksSpan || innerSource[tasksSpan.start] !== "[") continue;
      const rawTasks = splitJsonArrayElements(
        innerSource.slice(tasksSpan.start, tasksSpan.end)
      );
      if (!rawTasks || rawTasks.length === 0) continue;
      return {
        prefix,
        outerSource,
        trailingWhitespace,
        payloadSpan,
        innerSource,
        tasksSpan,
        rawTasks
      };
    }
    return null;
  }
  function findTopLevelPropertyValueSpan(source, propertyName) {
    let index = skipWhitespace(source, 0);
    if (source[index] !== "{") return null;
    index += 1;
    let match = null;
    while (index < source.length) {
      index = skipWhitespace(source, index);
      if (source[index] === "}") return match;
      if (source[index] !== '"') return null;
      const keyEnd = scanJsonStringEnd(source, index);
      if (keyEnd < 0) return null;
      const key = JSON.parse(source.slice(index, keyEnd));
      index = skipWhitespace(source, keyEnd);
      if (source[index] !== ":") return null;
      index = skipWhitespace(source, index + 1);
      const valueStart = index;
      const valueEnd = scanJsonValueEnd(source, valueStart);
      if (valueEnd < 0) return null;
      if (key === propertyName) {
        if (match) return null;
        match = { start: valueStart, end: valueEnd };
      }
      index = skipWhitespace(source, valueEnd);
      if (source[index] === ",") {
        index += 1;
        continue;
      }
      if (source[index] === "}") return match;
      return null;
    }
    return null;
  }
  function splitJsonArrayElements(source) {
    let index = skipWhitespace(source, 0);
    if (source[index] !== "[") return null;
    index += 1;
    const items = [];
    while (index < source.length) {
      index = skipWhitespace(source, index);
      if (source[index] === "]") return items;
      const start = index;
      const end = scanJsonValueEnd(source, start);
      if (end < 0) return null;
      items.push(source.slice(start, end));
      index = skipWhitespace(source, end);
      if (source[index] === ",") {
        index += 1;
        continue;
      }
      if (source[index] === "]") return items;
      return null;
    }
    return null;
  }
  function scanJsonValueEnd(source, start) {
    const first = source[start];
    if (first === '"') return scanJsonStringEnd(source, start);
    if (first === "{" || first === "[") {
      const stack = [first];
      let inString = false;
      let escaped = false;
      for (let index2 = start + 1; index2 < source.length; index2 += 1) {
        const char = source[index2];
        if (inString) {
          if (escaped) escaped = false;
          else if (char === "\\") escaped = true;
          else if (char === '"') inString = false;
          continue;
        }
        if (char === '"') {
          inString = true;
          continue;
        }
        if (char === "{" || char === "[") stack.push(char);
        else if (char === "}" || char === "]") {
          const open = stack.pop();
          if (open === "{" && char !== "}" || open === "[" && char !== "]")
            return -1;
          if (stack.length === 0) return index2 + 1;
        }
      }
      return -1;
    }
    let index = start;
    while (index < source.length && !/[\s,}\]]/.test(source[index])) index += 1;
    return index > start ? index : -1;
  }
  function scanJsonStringEnd(source, start) {
    let escaped = false;
    for (let index = start + 1; index < source.length; index += 1) {
      const char = source[index];
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') return index + 1;
    }
    return -1;
  }
  function skipWhitespace(source, start) {
    let index = start;
    while (index < source.length && /\s/.test(source[index])) index += 1;
    return index;
  }
  function replaceSpan(source, span, replacement) {
    return `${source.slice(0, span.start)}${replacement}${source.slice(span.end)}`;
  }
  function unchanged(value, blockedAll = false) {
    return { value, changed: false, blockedAll: !!blockedAll };
  }
  function changedBinary(input, encoded) {
    return {
      value: input.kind === "array-buffer" ? encoded.buffer : encoded,
      changed: true,
      blockedAll: false
    };
  }
  function getSupportedBytes(value) {
    const tag = Object.prototype.toString.call(value);
    if (tag === "[object ArrayBuffer]") {
      return {
        bytes: new Uint8Array(value),
        kind: "array-buffer"
      };
    }
    if (tag === "[object Uint8Array]" && ArrayBuffer.isView(value)) {
      return {
        bytes: new Uint8Array(
          value.buffer,
          value.byteOffset,
          value.byteLength
        ),
        kind: "uint8-array"
      };
    }
    return null;
  }
  function firstNonWhitespaceByte(bytes) {
    for (const byte of bytes) {
      if (byte !== 32 && byte !== 9 && byte !== 10 && byte !== 13)
        return byte;
    }
    return -1;
  }
  function bytesEqual(left, right) {
    if (left.byteLength !== right.byteLength) return false;
    for (let index = 0; index < left.byteLength; index += 1) {
      if (left[index] !== right[index]) return false;
    }
    return true;
  }

  // src/utils/network.js
  var DEFAULT_PATTERNS = {
    igTyping: [
      "indicate_activity",
      "typing_indicator",
      "activity_indicator",
      "is_typing",
      "direct_v2/threads/broadcast/typing",
      "direct_v2/threads/typing",
      "sendtypingindicator",
      "send_typing_indicator",
      "typing_on",
      "is_composing"
    ],
    igSeen: [
      "mark_read",
      "mark_seen",
      "thread_seen",
      "directmarkasseen",
      "markasseen",
      "directthreadmarkitemsseen",
      "polarisdirectmarkasseenmutation",
      "directseenmutation",
      "usepolarismarkthreadseenmutation",
      "useigdmarkthreadasreadmutation"
    ],
    igStory: [
      "storiesupdateseenmutation",
      "polarisstoriesseenmutation",
      "usepolarisstoriesv3seenmutation",
      "reelmediaseen",
      "storiesupdateseen",
      "seenstoriesupdatemutation",
      "polarisapireelseenmutation",
      "xdt_mark_story_reel_seen",
      "26997980659837802",
      "polarisapiforcestoryseenmutation",
      "xdt_api__v1__stories__reel__seen",
      "9647304595318258",
      "api/v1/stories/reel/seen",
      "stories/reel/seen",
      "mark_story_seen",
      "update_seen_for_reel",
      "reel_seen",
      "stories_update_seen",
      "mark_story_read"
    ],
    msgTyping: [
      "indicate_activity",
      "typing_indicator",
      "activity_indicator",
      "is_typing",
      "sendtypingindicator",
      "send_typing_indicator",
      "sendchatstate",
      "send_chat_state",
      "ajax/messaging/typ.php",
      "ajax/chat/typ.php",
      "ajax/mercury/typ.php",
      "thread_typing",
      "orca_typing_notifications",
      "is_composing",
      "iscomposing",
      "composing",
      "chat_state",
      "chatstate",
      "typing_status",
      "typingstate",
      "securetypingstate",
      "mawsecuretypingstate",
      "typingindicatorstoredprocedure",
      "istyping",
      "send_type"
    ],
    msgSeen: [
      "mark_read",
      "mark_seen",
      "thread_seen",
      "directmarkasseen",
      "markasseen",
      "directthreadmarkitemsseen",
      "polarisdirectmarkasseenmutation",
      "directseenmutation",
      "seenbyviewer",
      "updatelastseenat",
      "updatelastreadwatermark",
      "update_last_read_watermark",
      "sendreadreceipt",
      "lssendreadreceipt",
      "readreceipt",
      "read_receipt",
      "readreceiptmutation",
      "lsupdatethreadreadwatermark",
      "lsupdatelastreadwatermark",
      "last_read_watermark",
      "lastreadwatermark",
      "read_watermark",
      "readwatermark",
      "shouldsendreadreceipt",
      "should_send_read_receipt",
      "lsmarkthreadread",
      "mwmarkthreadread",
      "markasread",
      "change_read_status"
    ],
    msgStory: [
      "storiesupdateseenmutation",
      "polarisstoriesseenmutation",
      "usepolarisstoriesv3seenmutation",
      "reelmediaseen",
      "storiesupdateseen",
      "seenstoriesupdatemutation",
      "mark_story_seen",
      "update_seen_for_reel",
      "reel_seen",
      "stories_update_seen",
      "mark_story_read"
    ]
  };
  var PATTERN_KEYS = Object.keys(DEFAULT_PATTERNS);
  var MAX_PATTERNS_PER_FEATURE = 50;
  var MAX_PATTERN_LENGTH = 96;
  var PATTERNS = clonePatterns(DEFAULT_PATTERNS);
  function updatePatterns(newPatterns) {
    PATTERNS = mergePatterns(DEFAULT_PATTERNS, newPatterns);
  }
  function clonePatterns(patterns) {
    return Object.fromEntries(
      PATTERN_KEYS.map((key) => [key, [...patterns[key]]])
    );
  }
  function mergePatterns(defaults, incoming) {
    const merged = clonePatterns(defaults);
    if (!incoming || typeof incoming !== "object") return merged;
    for (const key of PATTERN_KEYS) {
      const remotePatterns = sanitizePatternList(incoming[key]);
      if (!remotePatterns.length) continue;
      const existing = new Set(merged[key]);
      for (const pattern of remotePatterns) existing.add(pattern);
      merged[key] = [...existing].slice(0, MAX_PATTERNS_PER_FEATURE);
    }
    return merged;
  }
  function sanitizePatternList(patterns) {
    if (!Array.isArray(patterns)) return [];
    return patterns.filter((pattern) => typeof pattern === "string").map((pattern) => pattern.trim().toLowerCase()).filter((pattern) => pattern && pattern.length <= MAX_PATTERN_LENGTH).filter((pattern) => !/[|^$()[\]{}+?\\]/.test(pattern)).slice(0, MAX_PATTERNS_PER_FEATURE);
  }
  function decode(data, limit = 15e3) {
    if (!data) return "";
    try {
      if (typeof data === "string") return withDecodedText(data, limit);
      if (data instanceof ArrayBuffer) {
        return withDecodedText(
          new TextDecoder().decode(data.slice(0, limit)),
          limit
        );
      }
      if (ArrayBuffer.isView(data)) {
        const view = new Uint8Array(
          data.buffer,
          data.byteOffset,
          Math.min(data.byteLength, limit)
        );
        return withDecodedText(new TextDecoder().decode(view), limit);
      }
      if (typeof URLSearchParams !== "undefined" && data instanceof URLSearchParams) {
        return withDecodedText(data.toString(), limit);
      }
      if (typeof FormData !== "undefined" && data instanceof FormData) {
        let text = "";
        for (const pair of data.entries()) {
          if (typeof pair[1] === "string") {
            text += `${pair[0]}=${pair[1]}&`;
            if (text.length >= limit) return text.slice(0, limit);
          }
        }
        return withDecodedText(text, limit);
      }
      if (typeof data === "object")
        return withDecodedText(JSON.stringify(data), limit);
    } catch (e) {
      return "";
    }
    return "";
  }
  function withDecodedText(text, limit) {
    const raw = String(text || "").slice(0, limit);
    try {
      const decoded = decodeURIComponent(raw.replace(/\+/g, " "));
      if (decoded && decoded !== raw) {
        return `${raw} ${decoded}`.slice(0, limit * 2);
      }
    } catch (e) {
    }
    return raw;
  }
  function matchesPattern(str, patternList) {
    if (!patternList || !Array.isArray(patternList)) return false;
    return patternList.some((pattern) => str.includes(pattern));
  }
  function includesAny(str, terms) {
    return terms.some((term) => str.includes(term));
  }
  function escapeRegExp(str) {
    return String(str || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }
  function hasTruthyField(str, fields) {
    const text = String(str || "");
    return fields.some((field) => {
      const escaped = escapeRegExp(field);
      return new RegExp(
        `(?:^|[&\\s"{,])${escaped}"?\\s*[:=]\\s*(?:"?(?:true|1)"?)`
      ).test(text);
    });
  }
  function hasFieldValue(str, fields, values) {
    const text = String(str || "");
    return fields.some((field) => {
      const escapedField = escapeRegExp(field);
      return values.some((value) => {
        const escapedValue = escapeRegExp(value);
        return new RegExp(
          `(?:^|[&\\s"{,])${escapedField}"?\\s*[:=]\\s*"?(?:${escapedValue})"?`
        ).test(text);
      });
    });
  }
  function includesStandaloneTerm(str, terms) {
    const text = String(str || "");
    return terms.some((term) => {
      const escaped = escapeRegExp(term);
      return new RegExp(`(?:^|[^a-z0-9_])${escaped}(?:$|[^a-z0-9_])`).test(
        text
      );
    });
  }
  function stripFalseyPrivacyFields(str) {
    const text = String(str || "");
    let decoded = "";
    try {
      decoded = decodeURIComponent(text.replace(/\+/g, " "));
    } catch (e) {
    }
    return `${stripFalseyPrivacyFieldsOnce(text)} ${stripFalseyPrivacyFieldsOnce(decoded)}`;
  }
  function stripFalseyPrivacyFieldsOnce(str) {
    return String(str || "").replace(
      /"?(?:shouldsendreadreceipt|should_send_read_receipt|sendreadreceipt|send_read_receipt|readreceipt|read_receipt|markread|mark_read|markseen|mark_seen|markasread|mark_as_read|threadseen|thread_seen|seenbyviewer|seen_by_viewer|istyping|is_typing|iscomposing|is_composing|typingindicator|typing_indicator)"?\s*[:=]\s*"?(?:false|0|null)"?/g,
      ""
    ).replace(
      /(?:%22)?(?:shouldsendreadreceipt|should_send_read_receipt|sendreadreceipt|send_read_receipt|readreceipt|read_receipt|markread|mark_read|markseen|mark_seen|markasread|mark_as_read|threadseen|thread_seen|seenbyviewer|seen_by_viewer|istyping|is_typing|iscomposing|is_composing|typingindicator|typing_indicator)(?:%22)?\s*(?:%3a|%3d)\s*(?:false|0|null)/g,
      ""
    );
  }
  function hasExplicitStorySeenSignal(str) {
    return includesAny(str, [
      "storiesupdateseenmutation",
      "polarisstoriesseenmutation",
      "usepolarisstoriesv3seenmutation",
      "reelmediaseen",
      "storiesupdateseen",
      "seenstoriesupdatemutation",
      "mark_story_seen",
      "update_seen_for_reel",
      "reel_seen",
      "stories_update_seen",
      "mark_story_read"
    ]);
  }
  function isFacebookMobileStorySeenWebLiteFrame(data, urlString) {
    if (!isFacebookMobileStoryViewer()) return false;
    if (!isFacebookWebLiteSocket(urlString)) return false;
    const bytes = getBinaryFrameBytes(data);
    if (!bytes || bytes.byteLength !== 54) return false;
    const declaredLength = bytes[0] << 8 | bytes[1];
    if (declaredLength !== bytes.byteLength - 2 || bytes[2] !== 83)
      return false;
    if (!bytesAreZero(bytes, 23, 31)) return false;
    if (bytes[31] !== 66) return false;
    if (!bytesAre(bytes, 36, 40, 255)) return false;
    return bytes[48] === 12;
  }
  function isFacebookMobileStoryViewer() {
    try {
      return window.location.hostname.toLowerCase() === "m.facebook.com" && String(window.location.pathname || "").toLowerCase().startsWith("/stories/");
    } catch (e) {
      return false;
    }
  }
  function isFacebookWebLiteSocket(urlString) {
    try {
      const url = new URL(String(urlString || ""), window.location.href);
      return url.protocol === "wss:" && url.hostname === "kaios-d.facebook.com" && url.pathname.startsWith("/ws/");
    } catch (e) {
      return false;
    }
  }
  function getBinaryFrameBytes(data) {
    try {
      if (data instanceof ArrayBuffer) return new Uint8Array(data);
      if (ArrayBuffer.isView(data)) {
        return new Uint8Array(
          data.buffer,
          data.byteOffset,
          data.byteLength
        );
      }
    } catch (e) {
    }
    return null;
  }
  function bytesAreZero(bytes, start, end) {
    return bytesAre(bytes, start, end, 0);
  }
  function bytesAre(bytes, start, end, expected) {
    for (let index = start; index < end; index += 1) {
      if (bytes[index] !== expected) return false;
    }
    return true;
  }
  function isInstagramStoryViewerLookup(str) {
    if (hasExplicitStorySeenSignal(str)) return false;
    return includesAny(str, [
      "stories_viewer",
      "story_viewer",
      "story_viewers",
      "reel_media_viewers",
      "reel_viewers",
      "viewer_list",
      "viewers_list",
      "seen_by",
      "seenby"
    ]);
  }
  function hasFacebookMessengerContext(str) {
    return includesAny(str, [
      "messenger",
      "message",
      "messages",
      "messaging",
      "cometmessenger",
      "mwchat",
      "mailbox",
      "inbox",
      "maw",
      "lsplatform",
      "thread_key",
      "threadkey",
      "thread_fbid",
      "threadfbid",
      "thread_id",
      "threadid",
      "recipient_id",
      "message_thread",
      "act_thread_id",
      "parent_thread_key",
      "parentthreadkey",
      "open_message_thread_key",
      "openmessagethreadkey",
      "armadillo_thread_key",
      "armadillothreadkey",
      "other_user_fbid",
      "otheruserfbid",
      "other_user_id",
      "otheruserid",
      "target_id",
      "targetid"
    ]);
  }
  function hasMessengerReadReceiptSignal(str) {
    const text = stripFalseyPrivacyFields(str);
    return hasMessengerReadReceiptWriteSignal(text) || includesAny(text, [
      "last_read_watermark",
      "lastreadwatermark",
      "last_read_watermark_ts",
      "lastreadwatermarkts",
      "read_watermark",
      "readwatermark",
      "watermarktimestamp",
      "watermark_timestamp",
      "seenbyviewer",
      "seen_by_viewer"
    ]);
  }
  function hasMessengerReadReceiptWriteSignal(str) {
    const text = stripFalseyPrivacyFields(str);
    return includesAny(text, [
      "markthreadasread",
      "mark_thread_read",
      "markthreadreadmutation",
      "markthreadread",
      "markread",
      "mark_read",
      "markseen",
      "mark_seen",
      "threadseen",
      "thread_seen",
      "change_read_status",
      "updatelastseenat",
      "updatelastreadwatermark",
      "update_last_read_watermark",
      "sendreadreceipt",
      "lssendreadreceipt",
      "readreceiptmutation",
      "readreceipt",
      "read_receipt",
      "lsupdatethreadreadwatermark",
      "lsmarkthreadread",
      "mwmarkthreadread",
      "lsupdatelastreadwatermark",
      "markasread",
      "mark_as_read",
      "shouldsendreadreceipt",
      "should_send_read_receipt"
    ]) || hasTruthyField(str, [
      "shouldsendreadreceipt",
      "should_send_read_receipt",
      "sendreadreceipt",
      "send_read_receipt",
      "readreceipt",
      "read_receipt",
      "markread",
      "mark_read",
      "markseen",
      "mark_seen",
      "markasread",
      "mark_as_read",
      "threadseen",
      "thread_seen",
      "seenbyviewer",
      "seen_by_viewer"
    ]);
  }
  function hasReadReceiptWriteContext(str) {
    return hasReadReceiptOperationContext(str) && (hasMessengerThreadContext(str) || hasReadReceiptWatermarkContext(str));
  }
  function hasReadReceiptOperationContext(str) {
    return includesAny(str, [
      "mutation",
      "procedure",
      "storedprocedure",
      "ls_req",
      "/ls_req",
      "issue_new_task",
      "issuenewtask"
    ]);
  }
  function hasMessengerThreadContext(str) {
    return includesAny(str, [
      "thread_key",
      "threadkey",
      "thread_fbid",
      "threadfbid",
      "thread_id",
      "threadid",
      "recipient_id",
      "message_thread",
      "act_thread_id",
      "parent_thread_key",
      "parentthreadkey",
      "open_message_thread_key",
      "openmessagethreadkey",
      "armadillo_thread_key",
      "armadillothreadkey",
      "other_user_fbid",
      "otheruserfbid",
      "other_user_id",
      "otheruserid",
      "target_id",
      "targetid"
    ]);
  }
  function hasReadReceiptWatermarkContext(str) {
    return includesAny(str, [
      "last_read_watermark",
      "lastreadwatermark",
      "last_read_watermark_ts",
      "lastreadwatermarkts",
      "read_watermark",
      "readwatermark",
      "last_seen_time_ms",
      "lastseentimems",
      "watermarktimestamp",
      "watermark_timestamp",
      "shouldsendreadreceipt",
      "should_send_read_receipt"
    ]);
  }
  function hasFacebookMessengerTypingWriteIntent(str) {
    return includesAny(str, [
      "sendtypingindicator",
      "lssendtypingindicator",
      "lssendtypingindicatorstoredprocedure",
      "send_typing_indicator",
      "send_typing",
      "sendchatstatefromcomposer",
      "sendchatstate",
      "send_chat_state",
      "typingindicatorstoredprocedure",
      "mawsecuretypingstate",
      "securetypingstate"
    ]) || hasTruthyField(str, [
      "istyping",
      "is_typing",
      "iscomposing",
      "is_composing",
      "typingindicator",
      "typing_indicator"
    ]) || hasFieldValue(
      str,
      [
        "chatstate",
        "chat_state",
        "typingstate",
        "typing_status",
        "send_type"
      ],
      ["typing", "composing", "typing_indicator"]
    );
  }
  function hasMessageRequestContext(str, urlString = "") {
    const text = `${str} ${urlString}`;
    return includesAny(text, [
      "message_requests",
      "message request",
      "message_request",
      "messagerequests",
      "message-requests",
      "/requests",
      "pending_threads",
      "pendingthreads",
      "filtered_threads",
      "filteredthreads",
      "spam_threads",
      "spamthreads"
    ]);
  }
  function hasExplicitMessengerReadWriteCommand(str) {
    const text = stripFalseyPrivacyFields(str);
    const hasOperationValuedReadWrite = hasFieldValue(
      text,
      ["operation", "procedure", "command", "task_name"],
      [
        "mark_read",
        "mark_seen",
        "mark_as_read",
        "thread_seen",
        "read_receipt",
        "updatelastseenat",
        "updatelastreadwatermark",
        "update_last_read_watermark"
      ]
    );
    return hasOperationValuedReadWrite || includesAny(text, [
      "markthreadasread",
      "mark_thread_read",
      "markthreadreadmutation",
      "markthreadread",
      "lsmarkthreadread",
      "mwmarkthreadread",
      "lssendreadreceipt",
      "readreceiptmutation",
      "lsupdatethreadreadwatermark",
      "lsupdatelastreadwatermark",
      "updatelastreadwatermark",
      "update_last_read_watermark",
      "change_read_status"
    ]) || includesStandaloneTerm(text, [
      "sendreadreceipt",
      "send_read_receipt"
    ]) || hasTruthyField(text, [
      "shouldsendreadreceipt",
      "should_send_read_receipt",
      "sendreadreceipt",
      "send_read_receipt",
      "readreceipt",
      "read_receipt",
      "markread",
      "mark_read",
      "markseen",
      "mark_seen",
      "markasread",
      "mark_as_read",
      "threadseen",
      "thread_seen",
      "seenbyviewer",
      "seen_by_viewer"
    ]);
  }
  function isMessageRequestHydrationRequest(str, urlString, method) {
    if (!hasMessageRequestContext(str, urlString)) return false;
    if (hasExplicitMessengerReadWriteCommand(str)) return false;
    if (isMessengerTypingWrite(str, urlString)) return false;
    return isGraphQLRequest(str, urlString) || isMessengerRealtimeTransport(urlString) || includesAny(str, [
      "ls_req",
      "/ls_req",
      "issue_new_task",
      "issuenewtask",
      "threadlist",
      "thread_list",
      "messagerequestsquery",
      "routepreload",
      "route_preload",
      "fetch_thread_list",
      "mwchat_fetch_thread_list",
      "folder",
      "pagination",
      "cursor"
    ]) || method === "GET" || method === "HEAD";
  }
  function isMessengerReadReceiptWrite(str, urlString) {
    if (isLegacyMessengerReadEndpoint(urlString)) return true;
    if (hasMessengerMessageSendIntent(str)) return false;
    if (isMessengerRealtimeReadBridgeWrite(str, urlString)) return true;
    if (isMessengerSendWithBundledReadWatermark(str)) return false;
    if (isMessageRequestHydrationRequest(str, urlString, "")) return false;
    if (hasMessengerReadReceiptSignal(str)) {
      return hasReadReceiptWriteContext(str);
    }
    if (!hasReadReceiptWriteContext(str)) return false;
    const hasReceiptField = includesAny(str, [
      "last_read_watermark",
      "lastreadwatermark",
      "last_read_watermark_ts",
      "lastreadwatermarkts",
      "read_watermark",
      "readwatermark",
      "watermarktimestamp",
      "watermark_timestamp",
      "read_receipt",
      "readreceipt",
      "seenbyviewer",
      "seen_by_viewer",
      "shouldsendreadreceipt",
      "should_send_read_receipt"
    ]);
    if (!hasReceiptField) return false;
    return includesAny(str, [
      "mark_read",
      "markread",
      "mark_seen",
      "markseen",
      "thread_seen",
      "threadseen",
      "change_read_status",
      "ids["
    ]);
  }
  function isLegacyMessengerReadEndpoint(urlString) {
    return includesAny(urlString, [
      "/ajax/mercury/change_read_status.php",
      "/ajax/mercury/mark_read.php",
      "/ajax/mercury/mark_seen.php",
      "/ajax/mercury/mark_thread_read.php",
      "/ajax/mercury/read_receipts.php",
      "/ajax/messaging/read_receipts.php",
      "/ajax/chat/read_receipts.php"
    ]);
  }
  function isMessengerRealtimeReadBridgeWrite(str, urlString) {
    str = stripFalseyPrivacyFields(str);
    if (!isMessengerRealtimeTransport(urlString)) return false;
    if (str.includes("delivery_receipt") && !hasMessengerReadReceiptWriteSignal(str))
      return false;
    if (!hasMessengerReadReceiptWriteSignal(str) && !hasRealtimeReadWatermarkWriteSignal(str))
      return false;
    return includesAny(str, [
      "markthread",
      "markread",
      "mark_read",
      "markseen",
      "mark_seen",
      "markasread",
      "mark_as_read",
      "readreceipt",
      "read_receipt",
      "lastreadwatermark",
      "last_read_watermark",
      "last_read_watermark_ts",
      "lastreadwatermarkts",
      "read_watermark",
      "readwatermark",
      "last_seen_time_ms",
      "lastseentimems",
      "watermarktimestamp",
      "watermark_timestamp",
      "shouldsendreadreceipt",
      "should_send_read_receipt",
      "sendreadreceipt",
      "lsmarkthreadread",
      "lsupdatethreadreadwatermark",
      "updatelastreadwatermark",
      "update_last_read_watermark",
      "mwmarkthreadread",
      "change_read_status"
    ]);
  }
  function hasRealtimeReadWatermarkWriteSignal(str) {
    str = stripFalseyPrivacyFields(str);
    if (str.includes("send_type") && !hasMessengerReadReceiptWriteSignal(str))
      return false;
    const hasWatermark = includesAny(str, [
      "last_read_watermark",
      "lastreadwatermark",
      "last_read_watermark_ts",
      "lastreadwatermarkts",
      "read_watermark",
      "readwatermark",
      "last_seen_time_ms",
      "lastseentimems",
      "watermarktimestamp",
      "watermark_timestamp",
      "shouldsendreadreceipt",
      "should_send_read_receipt"
    ]);
    if (!hasWatermark) return false;
    if (isRealtimeReadReceiptLabelTask(str)) return true;
    return includesAny(str, [
      "markthread",
      "markread",
      "mark_read",
      "markasread",
      "sendreadreceipt",
      "readreceipt",
      "read_receipt",
      "lsupdatethreadreadwatermark",
      "lsupdatelastreadwatermark",
      "updatelastreadwatermark",
      "update_last_read_watermark",
      "shouldsendreadreceipt",
      "should_send_read_receipt",
      "storedprocedure"
    ]);
  }
  function isRealtimeReadReceiptLabelTask(str) {
    return isRealtimeReadWatermarkLabelTask(str) || isRealtimeLastSeenLabelTask(str);
  }
  function isRealtimeReadWatermarkLabelTask(str) {
    return hasSerializedFieldValue(str, "label", "21") && includesAny(str, ["last_read_watermark_ts", "lastreadwatermarkts"]) && hasMessengerThreadContext(str);
  }
  function isRealtimeLastSeenLabelTask(str) {
    return hasSerializedFieldValue(str, "label", "6") && includesAny(str, ["last_seen_time_ms", "lastseentimems"]) && includesAny(str, ["parent_thread_key", "parentthreadkey"]);
  }
  function hasSerializedFieldValue(str, field, value) {
    const unescaped = String(str || "").replace(/\\/g, "");
    return includesAny(str, [
      `"${field}":"${value}"`,
      `"${field}": "${value}"`,
      `\\"${field}\\":\\"${value}\\"`,
      `\\"${field}\\": \\"${value}\\"`,
      `%22${field}%22%3a%22${value}%22`,
      `%22${field}%22%3A%22${value}%22`
    ]) || includesAny(unescaped, [
      `"${field}":"${value}"`,
      `"${field}": "${value}"`
    ]);
  }
  function normalizeMessengerPayloadKey(key) {
    return String(key || "").toLowerCase().replace(/[^a-z0-9]/g, "");
  }
  function normalizedMessengerPayloadFields(value) {
    const fields = {};
    try {
      for (const key of Object.keys(value || {})) {
        fields[normalizeMessengerPayloadKey(key)] = value[key];
      }
    } catch (e) {
    }
    return fields;
  }
  function parseMessengerPayloadObject(value) {
    if (value && typeof value === "object" && !Array.isArray(value))
      return value;
    if (typeof value !== "string") return null;
    try {
      const parsed = JSON.parse(value);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
    } catch (e) {
      return null;
    }
  }
  function isPositiveMessengerSendType(value) {
    if (typeof value === "number") return Number.isFinite(value) && value > 0;
    if (typeof value !== "string") return false;
    const normalized = value.trim();
    return /^\d+$/.test(normalized) && Number(normalized) > 0;
  }
  function getQueueRoutedMessengerSendTask(value) {
    if (!value || typeof value !== "object" || Array.isArray(value))
      return null;
    const fields = normalizedMessengerPayloadFields(value);
    if (String(fields.label || "") !== "46") return null;
    if (typeof fields.queuename !== "string" || !fields.queuename.trim())
      return null;
    const payloadKey = Object.keys(value).find(
      (key) => normalizeMessengerPayloadKey(key) === "payload"
    );
    if (!payloadKey) return null;
    const rawPayload = value[payloadKey];
    const payload = parseMessengerPayloadObject(rawPayload);
    if (!payload) return null;
    const payloadFields = normalizedMessengerPayloadFields(payload);
    const sendType = payloadFields.sendtype;
    if (!isPositiveMessengerSendType(sendType)) return null;
    const hasClientMessageId = [
      "offlinethreadingid",
      "clientmessageid",
      "clientmutationid",
      "otid"
    ].some(
      (key) => payloadFields[key] != null && String(payloadFields[key]).length > 0
    );
    const hasMessagePayload = [
      "message",
      "text",
      "body",
      "attachment",
      "sticker",
      "media",
      "encryptedmessage",
      "encryptedpayload",
      "encryptedblob",
      "encryptedcontent",
      "ciphertext",
      "reaction",
      "messagereaction",
      "emoji",
      "quicklike"
    ].some((key) => payloadFields[key] != null);
    return hasClientMessageId && hasMessagePayload ? { payloadKey, rawPayload, payload } : null;
  }
  function isNetworkReadReceiptSendFlagKey(key) {
    return key === "shouldsendreadreceipt" || key === "sendreadreceipt" || key === "sendreadreceipts" || key === "sendreadreceiptflag" || key === "readreceiptenabled";
  }
  function isNetworkReadReceiptMutationKey(key) {
    return key === "lssendreadreceipt" || key === "readreceiptmutation" || key === "sendreadreceiptmutation";
  }
  function isNetworkReadWatermarkKey(key) {
    return key === "lastreadwatermark" || key === "lastreadwatermarkts" || key === "lastseentimems" || key === "readwatermark" || key === "watermarktimestamp";
  }
  var NETWORK_MESSAGE_CONTENT_KEYS = /* @__PURE__ */ new Set([
    "text",
    "body",
    "attachment",
    "sticker",
    "media",
    "encryptedmessage",
    "encryptedpayload",
    "encryptedblob",
    "encryptedcontent",
    "ciphertext",
    "reaction",
    "messagereaction",
    "emoji",
    "quicklike"
  ]);
  function isNetworkMessageContentKey(key) {
    return NETWORK_MESSAGE_CONTENT_KEYS.has(key);
  }
  function isLikelyNetworkReadWatermarkValue(value) {
    if (typeof value === "number")
      return Number.isFinite(value) && value >= 1e9;
    if (typeof value === "string") return /^\d{10,}$/.test(value.trim());
    return false;
  }
  function safeNetworkReadWatermarkValue(value) {
    const text = String(value).trim();
    const safe = `1${"0".repeat(Math.max(0, text.length - 1))}`;
    return typeof value === "number" ? Number(safe) : safe;
  }
  function isTruthyNetworkPrivacyValue(value) {
    if (value === true || value === 1) return true;
    if (typeof value === "string") {
      const normalized = value.trim().toLowerCase();
      return normalized === "true" || normalized === "1";
    }
    return !!(value && typeof value === "object");
  }
  function sanitizeQueueSendPayloadReadMetadata(value, depth = 0) {
    if (!value || depth > 6 || typeof value !== "object") {
      return { value, changed: false };
    }
    if (Array.isArray(value)) {
      let changed2 = false;
      const next = value.map((item) => {
        const sanitized = sanitizeQueueSendPayloadReadMetadata(
          item,
          depth + 1
        );
        changed2 = changed2 || sanitized.changed;
        return sanitized.value;
      });
      return { value: changed2 ? next : value, changed: changed2 };
    }
    let changed = false;
    const clone = {};
    for (const key of Object.keys(value)) {
      const child = value[key];
      const normalizedKey = normalizeMessengerPayloadKey(key);
      if (isNetworkMessageContentKey(normalizedKey)) {
        clone[key] = child;
        continue;
      }
      if (isNetworkReadReceiptSendFlagKey(normalizedKey)) {
        const truthy = isTruthyNetworkPrivacyValue(child);
        clone[key] = truthy ? false : child;
        changed = changed || truthy;
        continue;
      }
      if (isNetworkReadReceiptMutationKey(normalizedKey)) {
        const truthy = isTruthyNetworkPrivacyValue(child);
        clone[key] = truthy ? null : child;
        changed = changed || truthy;
        continue;
      }
      if (isNetworkReadWatermarkKey(normalizedKey) && isLikelyNetworkReadWatermarkValue(child)) {
        clone[key] = safeNetworkReadWatermarkValue(child);
        changed = changed || clone[key] !== child;
        continue;
      }
      const sanitized = sanitizeQueueSendPayloadReadMetadata(
        child,
        depth + 1
      );
      clone[key] = sanitized.value;
      changed = changed || sanitized.changed;
    }
    return { value: changed ? clone : value, changed };
  }
  function sanitizeQueueRoutedMessengerSendTask(value) {
    const task = getQueueRoutedMessengerSendTask(value);
    if (!task || !SETTINGS.msgSeen || isKilled("msgSeen")) {
      return { value, changed: false, blockedAll: false };
    }
    let clone = Object.assign({}, value);
    let changed = false;
    if (typeof task.rawPayload === "string") {
      const sanitizedSource = sanitizeJsonTaskBatchStringSource(
        task.rawPayload,
        (candidate) => {
          const sanitized = sanitizeQueueSendPayloadReadMetadata(candidate);
          return {
            value: sanitized.value,
            changed: sanitized.changed,
            blockedAll: false
          };
        }
      );
      if (sanitizedSource.changed && !sanitizedSource.blockedAll && typeof sanitizedSource.value === "string") {
        clone[task.payloadKey] = sanitizedSource.value;
        changed = true;
      }
    } else {
      const sanitized = sanitizeQueueSendPayloadReadMetadata(task.payload);
      if (sanitized.changed) {
        clone[task.payloadKey] = sanitized.value;
        changed = true;
      }
    }
    const envelope = {};
    for (const key of Object.keys(clone)) {
      if (key !== task.payloadKey) envelope[key] = clone[key];
    }
    const sanitizedEnvelope = sanitizeQueueSendPayloadReadMetadata(envelope);
    if (sanitizedEnvelope.changed) {
      clone = Object.assign({}, sanitizedEnvelope.value, {
        [task.payloadKey]: clone[task.payloadKey]
      });
      changed = true;
    }
    return { value: changed ? clone : value, changed, blockedAll: false };
  }
  function parseMessengerStructuredText(value) {
    const source = String(value || "").trim();
    if (!source) return null;
    const candidates = [source];
    const nullIndex = source.lastIndexOf("\0");
    if (nullIndex >= 0) candidates.unshift(source.slice(nullIndex + 1).trim());
    for (const candidate of candidates) {
      if (!candidate || candidate[0] !== "{" && candidate[0] !== "[")
        continue;
      try {
        return JSON.parse(candidate);
      } catch (e) {
        const isolated = isolateFirstMessengerJsonValue(candidate);
        if (!isolated) continue;
        try {
          return JSON.parse(isolated);
        } catch (parseError) {
        }
      }
    }
    return null;
  }
  function isolateFirstMessengerJsonValue(source) {
    const first = source[0];
    if (first !== "{" && first !== "[") return null;
    const stack = [first];
    let inString = false;
    let escaped = false;
    for (let index = 1; index < source.length; index += 1) {
      const char = source[index];
      if (inString) {
        if (escaped) escaped = false;
        else if (char === "\\") escaped = true;
        else if (char === '"') inString = false;
        continue;
      }
      if (char === '"') {
        inString = true;
        continue;
      }
      if (char === "{" || char === "[") stack.push(char);
      else if (char === "}" || char === "]") {
        const open = stack.pop();
        if (open === "{" && char !== "}" || open === "[" && char !== "]")
          return null;
        if (stack.length === 0) return source.slice(0, index + 1);
      }
    }
    return null;
  }
  function hasUnsafeNetworkPrivacyMetadata(value, depth = 0) {
    if (!value || depth > 8) return false;
    if (typeof value === "string") {
      const parsed = parseMessengerStructuredText(value);
      return parsed ? hasUnsafeNetworkPrivacyMetadata(parsed, depth + 1) : false;
    }
    if (Array.isArray(value)) {
      return value.some(
        (item) => hasUnsafeNetworkPrivacyMetadata(item, depth + 1)
      );
    }
    if (typeof value !== "object") return false;
    const fields = normalizedMessengerPayloadFields(value);
    const label = normalizeMessengerPayloadKey(fields.label);
    if (label === "6" || label === "21") return true;
    for (const key of Object.keys(value)) {
      const child = value[key];
      const normalizedKey = normalizeMessengerPayloadKey(key);
      if (isNetworkMessageContentKey(normalizedKey)) continue;
      if ((isNetworkReadReceiptSendFlagKey(normalizedKey) || isNetworkReadReceiptMutationKey(normalizedKey)) && isTruthyNetworkPrivacyValue(child)) {
        return true;
      }
      if (isNetworkReadWatermarkKey(normalizedKey) && isLikelyNetworkReadWatermarkValue(child) && safeNetworkReadWatermarkValue(child) !== child) {
        return true;
      }
      if (hasUnsafeNetworkPrivacyMetadata(child, depth + 1)) return true;
    }
    return false;
  }
  function inspectQueueRoutedMessengerEnvelope(value, depth = 0, state = { send: false, unsafe: false }) {
    if (!value || depth > 8 || state.unsafe) return state;
    if (typeof value === "string") {
      const parsed = parseMessengerStructuredText(value);
      if (parsed)
        inspectQueueRoutedMessengerEnvelope(parsed, depth + 1, state);
      return state;
    }
    if (Array.isArray(value)) {
      for (const item of value)
        inspectQueueRoutedMessengerEnvelope(item, depth + 1, state);
      return state;
    }
    if (typeof value !== "object") return state;
    const task = getQueueRoutedMessengerSendTask(value);
    if (task) {
      state.send = true;
      state.unsafe = hasUnsafeNetworkPrivacyMetadata(value, depth + 1);
      return state;
    }
    if (hasUnsafeNetworkPrivacyMetadata(value, depth)) {
      state.unsafe = true;
      return state;
    }
    for (const child of Object.values(value)) {
      inspectQueueRoutedMessengerEnvelope(child, depth + 1, state);
    }
    return state;
  }
  function hasSafeQueueRoutedMessengerSendIntent(str) {
    const parsed = parseMessengerStructuredText(str);
    if (!parsed) return false;
    const state = inspectQueueRoutedMessengerEnvelope(parsed);
    return state.send && !state.unsafe;
  }
  function isMessengerSendWithBundledReadWatermark(str) {
    if (!str.includes("send_type")) return false;
    if (!hasReadReceiptWatermarkContext(str)) return false;
    if (hasMessengerReadReceiptWriteSignal(str)) return false;
    return hasReadReceiptOperationContext(str) && hasMessengerThreadContext(str);
  }
  function hasMessengerMessageSendIntent(str) {
    const text = String(str || "");
    const matchText = text.includes("\\") ? `${text} ${text.replace(/\\/g, "")}` : text;
    const hasQueueRoutedSend = hasSafeQueueRoutedMessengerSendIntent(text);
    if (!hasMessengerThreadContext(matchText) && !hasQueueRoutedSend)
      return false;
    if (hasQueueRoutedSend) return true;
    const hasSendOperationName = includesAny(matchText, [
      "send_message",
      "sendmessage",
      "message_send",
      "messagesend",
      "messenger_send_message",
      "messengersendmessage",
      "sendmessagemutation",
      "messengersendmessagemutation"
    ]);
    const hasClientMessageId = includesAny(matchText, [
      "offline_threading_id",
      "offlinethreadingid",
      "client_message_id",
      "clientmessageid",
      "client_mutation_id",
      "clientmutationid",
      "otid"
    ]);
    const hasMessagePayload = includesAny(matchText, [
      '"message"',
      "%22message%22",
      "message:",
      "message=",
      '"text"',
      "%22text%22",
      "text:",
      "text=",
      "body",
      "attachment",
      "sticker",
      "media",
      "encrypted_message",
      "encryptedmessage",
      "encrypted_payload",
      "encryptedpayload",
      "encrypted_blob",
      "encryptedblob",
      "encrypted_content",
      "encryptedcontent",
      "ciphertext",
      "reaction",
      "message_reaction",
      "messagereaction",
      "emoji",
      "quick_like",
      "quicklike",
      "like"
    ]);
    if (hasSendOperationName && (hasMessagePayload || hasClientMessageId || matchText.includes("send_type")))
      return true;
    return matchText.includes("send_type") && hasClientMessageId && hasMessagePayload;
  }
  function hasMessengerDeliveryAckIntent(str) {
    if (!hasMessengerThreadContext(str)) return false;
    if (hasMessengerMessageSendIntent(str)) return true;
    if (hasMessengerReadReceiptWriteSignal(str)) return false;
    return includesAny(str, [
      "delivery_receipt",
      "deliveryreceipt",
      "delivery_receipts",
      "message_delivered",
      "messagedelivered",
      "markdelivered",
      "mark_delivered"
    ]);
  }
  function sanitizeMessengerNetworkPayload(data, url = "", options = {}) {
    if (!isMessenger) return { data, changed: false };
    if (!shouldSanitizeMessengerNetworkPayload())
      return { data, changed: false };
    if (shouldBypassNativeMessageRequestTransport(data, url, options))
      return { data, changed: false };
    const urlString = String(url || "").toLowerCase();
    if ((isFacebookDotCom || isFacebookMessengerProxy) && isMessengerRealtimeTransport(urlString)) {
      const sanitizer = (value) => sanitizeMessengerNetworkValue(
        value,
        urlString,
        options,
        0,
        isMessengerReadReceiptOnlyNetworkWrite
      );
      const structuredBinary = sanitizeWholeJsonArrayBinary(data, sanitizer);
      if (structuredBinary.changed) {
        recordMessengerNetworkSanitization();
        return { data: structuredBinary.value, changed: true };
      }
      const framedBinary = sanitizeFramedJsonTaskBatchBinary(data, sanitizer);
      if (framedBinary.changed) {
        recordMessengerNetworkSanitization();
        return { data: framedBinary.value, changed: true };
      }
    }
    if (typeof URLSearchParams !== "undefined" && data instanceof URLSearchParams) {
      return sanitizeMessengerUrlSearchParams(data, urlString, options);
    }
    if (typeof data !== "string") return { data, changed: false };
    const trimmed = data.trim();
    if (!trimmed) {
      return { data, changed: false };
    }
    if (trimmed[0] !== "{" && trimmed[0] !== "[") {
      return sanitizeMessengerUrlEncodedPayload(data, urlString, options);
    }
    try {
      let sanitized = sanitizeJsonTaskBatchStringSource(
        data,
        (value, depth = 0) => sanitizeMessengerNetworkValue(value, urlString, options, depth)
      );
      if (!sanitized.changed) {
        const parsed = JSON.parse(trimmed);
        const fallback = sanitizeMessengerNetworkValue(
          parsed,
          urlString,
          options
        );
        sanitized = fallback.changed ? {
          value: fallback.blockedAll ? void 0 : JSON.stringify(fallback.value),
          changed: true,
          blockedAll: fallback.blockedAll
        } : fallback;
      }
      if (!sanitized.changed || sanitized.blockedAll)
        return { data, changed: false };
      try {
        if (typeof window !== "undefined") {
          window.__GHOSTIFY_SANITIZED_NETWORK_MESSAGES__ = (window.__GHOSTIFY_SANITIZED_NETWORK_MESSAGES__ || 0) + 1;
        }
      } catch (e) {
      }
      return { data: sanitized.value, changed: true };
    } catch (e) {
      return { data, changed: false };
    }
  }
  function sanitizeMessengerUrlSearchParams(params, urlString, options = {}) {
    try {
      const next = new URLSearchParams(params.toString());
      const changed = sanitizeMessengerUrlSearchParamsInPlace(
        next,
        urlString,
        options
      );
      return changed ? { data: next, changed: true } : { data: params, changed: false };
    } catch (e) {
      return { data: params, changed: false };
    }
  }
  function sanitizeMessengerUrlEncodedPayload(data, urlString, options = {}) {
    if (typeof URLSearchParams === "undefined") return { data, changed: false };
    try {
      const params = new URLSearchParams(data);
      const changed = sanitizeMessengerUrlSearchParamsInPlace(
        params,
        urlString,
        options
      );
      if (!changed) return { data, changed: false };
      return { data: params.toString(), changed: true };
    } catch (e) {
      return { data, changed: false };
    }
  }
  function sanitizeMessengerUrlSearchParamsInPlace(params, urlString, options = {}) {
    let changed = false;
    let removedPrivacyOnlyEntry = false;
    const entries = [...params.entries()];
    const nextEntries = [];
    for (const [key, value] of entries) {
      const trimmedValue = String(value || "").trim();
      if (!trimmedValue || trimmedValue[0] !== "{" && trimmedValue[0] !== "[") {
        nextEntries.push([key, value]);
        continue;
      }
      try {
        let sanitized = sanitizeJsonTaskBatchStringSource(
          value,
          (candidate, depth = 0) => sanitizeMessengerNetworkValue(
            candidate,
            urlString,
            options,
            depth
          )
        );
        if (!sanitized.changed) {
          const parsed = JSON.parse(trimmedValue);
          const fallback = sanitizeMessengerNetworkValue(
            parsed,
            urlString,
            options
          );
          sanitized = fallback.changed ? {
            value: fallback.blockedAll ? void 0 : JSON.stringify(fallback.value),
            changed: true,
            blockedAll: fallback.blockedAll
          } : fallback;
        }
        if (!sanitized.changed) {
          nextEntries.push([key, value]);
          continue;
        }
        if (sanitized.blockedAll) {
          removedPrivacyOnlyEntry = true;
          changed = true;
          continue;
        }
        nextEntries.push([key, sanitized.value]);
        changed = true;
      } catch (e) {
        nextEntries.push([key, value]);
      }
    }
    if (removedPrivacyOnlyEntry) {
      const retainedText = nextEntries.map(([, value]) => decode(value)).join(" ").toLowerCase();
      const hasSafeRetainedTask = isMessengerNormalThreadListPaginationTask(retainedText) || isMessageRequestHydrationRequest(retainedText, urlString, "");
      if (!hasMessengerMessageSendIntent(retainedText) && !hasMessengerDeliveryAckIntent(retainedText) && !hasSafeRetainedTask) {
        return false;
      }
    }
    if (changed) {
      for (const key of new Set(entries.map(([entryKey]) => entryKey))) {
        params.delete(key);
      }
      for (const [key, value] of nextEntries) {
        params.append(key, value);
      }
    }
    if (changed) recordMessengerNetworkSanitization();
    return changed;
  }
  function recordMessengerNetworkSanitization() {
    try {
      if (typeof window !== "undefined") {
        window.__GHOSTIFY_SANITIZED_NETWORK_MESSAGES__ = (window.__GHOSTIFY_SANITIZED_NETWORK_MESSAGES__ || 0) + 1;
      }
    } catch (e) {
    }
  }
  function shouldSanitizeMessengerNetworkPayload() {
    return SETTINGS.msgSeen && !isKilled("msgSeen") || SETTINGS.msgTyping && !isKilled("msgTyping");
  }
  function sanitizeMessengerNetworkValue(value, urlString, options, depth = 0, privacyOnlyPredicate = isMessengerPrivacyOnlyNetworkWrite) {
    if (!value || depth > 8) {
      return { value, changed: false, blockedAll: false };
    }
    if (typeof value === "string") {
      const trimmed = value.trim();
      if (trimmed[0] !== "{" && trimmed[0] !== "[") {
        return { value, changed: false, blockedAll: false };
      }
      try {
        let sanitized = sanitizeJsonTaskBatchStringSource(
          value,
          (candidate, nestedDepth = 0) => sanitizeMessengerNetworkValue(
            candidate,
            urlString,
            options,
            depth + 1 + nestedDepth,
            privacyOnlyPredicate
          )
        );
        if (!sanitized.changed) {
          const parsed = JSON.parse(trimmed);
          const fallback = sanitizeMessengerNetworkValue(
            parsed,
            urlString,
            options,
            depth + 1,
            privacyOnlyPredicate
          );
          sanitized = fallback.changed ? {
            value: fallback.blockedAll ? void 0 : JSON.stringify(fallback.value),
            changed: true,
            blockedAll: fallback.blockedAll
          } : fallback;
        }
        if (!sanitized.changed)
          return { value, changed: false, blockedAll: false };
        return {
          value: sanitized.blockedAll ? void 0 : sanitized.value,
          changed: true,
          blockedAll: sanitized.blockedAll
        };
      } catch (e) {
        return { value, changed: false, blockedAll: false };
      }
    }
    if (Array.isArray(value)) {
      let changed = false;
      const next = [];
      for (const item of value) {
        const sanitizedItem = sanitizeMessengerNetworkValue(
          item,
          urlString,
          options,
          depth + 1,
          privacyOnlyPredicate
        );
        if (sanitizedItem.blockedAll) {
          changed = true;
          continue;
        }
        const itemText = decode(sanitizedItem.value).toLowerCase();
        if (privacyOnlyPredicate(itemText, urlString, options)) {
          changed = true;
          continue;
        }
        changed = changed || sanitizedItem.changed;
        next.push(sanitizedItem.value);
      }
      return {
        value: changed ? next : value,
        changed,
        blockedAll: changed && next.length === 0
      };
    }
    if (typeof value === "object") {
      const queueRoutedSend = sanitizeQueueRoutedMessengerSendTask(value);
      if (queueRoutedSend.changed) return queueRoutedSend;
      const ownText = decode(value).toLowerCase();
      if (isMessengerNormalThreadListPaginationTask(ownText)) {
        return { value, changed: false, blockedAll: false };
      }
      const structuredBatch = hasMessengerStructuredBatch(value);
      if (!structuredBatch && (hasMessengerMessageSendIntent(ownText) || hasMessengerDeliveryAckIntent(ownText))) {
        return { value, changed: false, blockedAll: false };
      }
      if (!structuredBatch && privacyOnlyPredicate(ownText, urlString, options)) {
        return { value: void 0, changed: true, blockedAll: true };
      }
      let changed = false;
      const clone = {};
      for (const key of Object.keys(value)) {
        const child = value[key];
        const sanitizedChild = sanitizeMessengerNetworkValue(
          child,
          urlString,
          options,
          depth + 1,
          privacyOnlyPredicate
        );
        if (sanitizedChild.blockedAll) {
          changed = true;
          continue;
        }
        changed = changed || sanitizedChild.changed;
        clone[key] = sanitizedChild.value;
      }
      return {
        value: changed ? clone : value,
        changed,
        blockedAll: changed && (Object.keys(clone).length === 0 || structuredBatch && !hasMessengerStructuredBatchItems(clone))
      };
    }
    return { value, changed: false, blockedAll: false };
  }
  function isMessengerPrivacyOnlyNetworkWrite(str, urlString, options) {
    if (!str) return false;
    if (hasMessengerMessageSendIntent(str) || hasMessengerDeliveryAckIntent(str))
      return false;
    if (SETTINGS.msgSeen && !isKilled("msgSeen") && isMessengerReadReceiptNetworkTask(str, urlString, options)) {
      return true;
    }
    if (SETTINGS.msgTyping && !isKilled("msgTyping") && isMessengerTypingNetworkTask(str, urlString)) {
      return true;
    }
    return false;
  }
  function hasMessengerStructuredBatch(value) {
    if (!value || Array.isArray(value) || typeof value !== "object")
      return false;
    for (const [key, child] of Object.entries(value)) {
      const normalized = String(key || "").toLowerCase().replace(/[^a-z0-9]/g, "");
      if (["tasks", "tasklist", "batch"].includes(normalized) && Array.isArray(child))
        return true;
      if (normalized !== "payload" || typeof child !== "string") continue;
      const trimmed = child.trim();
      if (trimmed[0] !== "{") continue;
      try {
        const parsed = JSON.parse(trimmed);
        if (hasMessengerStructuredBatch(parsed)) return true;
      } catch (e) {
      }
    }
    return false;
  }
  function hasMessengerStructuredBatchItems(value) {
    if (!value || Array.isArray(value) || typeof value !== "object")
      return false;
    for (const [key, child] of Object.entries(value)) {
      const normalized = String(key || "").toLowerCase().replace(/[^a-z0-9]/g, "");
      if (["tasks", "tasklist", "batch"].includes(normalized) && Array.isArray(child)) {
        if (child.length > 0) return true;
        continue;
      }
      if (normalized !== "payload" || typeof child !== "string") continue;
      try {
        const parsed = JSON.parse(child.trim());
        if (hasMessengerStructuredBatchItems(parsed)) return true;
      } catch (e) {
      }
    }
    return false;
  }
  function isMessengerReadReceiptOnlyNetworkWrite(str, urlString, options) {
    if (!str || hasMessengerMessageSendIntent(str) || hasMessengerDeliveryAckIntent(str))
      return false;
    if (isMessengerNormalThreadListPaginationTask(str)) return false;
    return SETTINGS.msgSeen && !isKilled("msgSeen") && isMessengerReadReceiptNetworkTask(str, urlString, options);
  }
  function isMessengerReadReceiptNetworkTask(str, urlString, _options) {
    if (isMessengerNormalThreadListPaginationTask(str)) return false;
    if (isMessengerReadReceiptWrite(str, urlString)) return true;
    if (!hasMessengerThreadContext(str)) return false;
    const hasTaskEnvelope = includesAny(str, [
      "label",
      "queue_name",
      "queuename",
      "payload",
      "tasks"
    ]);
    if (!hasTaskEnvelope && !hasReadReceiptOperationContext(str)) return false;
    return hasMessengerReadReceiptWriteSignal(str);
  }
  function isMessengerNormalThreadListPaginationTask(str) {
    const text = String(str || "").toLowerCase();
    if (!includesAny(text, [
      "mwchatthreadlistpaginationquery",
      "mwchatthreadlistquery",
      "messengerthreadlistpaginationquery",
      "messengerthreadlistquery",
      "threadlistpaginationquery"
    ]))
      return false;
    if (!includesAny(text, [
      "cursor",
      "pagination",
      "mwchat_fetch_thread_list",
      "fetch_thread_list",
      "thread_list",
      "threadlist"
    ]))
      return false;
    if (includesAny(text, [
      "markthreadasread",
      "mark_thread_as_read",
      "markthreadread",
      "mark_thread_read",
      "markasread",
      "mark_as_read",
      "markseen",
      "mark_seen",
      "threadseen",
      "thread_seen",
      "change_read_status",
      "lssendreadreceipt",
      "readreceiptmutation",
      "sendreadreceiptmutation",
      "sendreadreceiptstoredprocedure",
      "optimisticmarkthreadread",
      "updatethreadreadwatermark",
      "updatelastseenat",
      "updatelastreadwatermark",
      "update_last_read_watermark"
    ]) || includesStandaloneTerm(text, ["sendreadreceipt", "send_read_receipt"]))
      return false;
    if (hasFieldValue(
      text,
      ["operation", "procedure", "command", "task_name"],
      [
        "thread_seen",
        "threadseen",
        "read_receipt",
        "readreceipt",
        "updatelastseenat",
        "updatelastreadwatermark",
        "update_last_read_watermark"
      ]
    ))
      return false;
    return !hasTruthyField(text, [
      "should_send_read_receipt",
      "shouldsendreadreceipt",
      "send_read_receipt",
      "sendreadreceipt",
      "read_receipt",
      "readreceipt",
      "mark_read",
      "markread",
      "mark_seen",
      "markseen",
      "thread_seen",
      "threadseen",
      "seen_by_viewer",
      "seenbyviewer"
    ]);
  }
  function isMessengerTypingNetworkTask(str, urlString) {
    if (isMessengerTypingWrite(str, urlString)) return true;
    if (!hasMessengerThreadContext(str)) return false;
    const hasTaskEnvelope = includesAny(str, [
      "label",
      "queue_name",
      "queuename",
      "payload",
      "tasks"
    ]);
    if (!hasTaskEnvelope && !hasReadReceiptOperationContext(str)) return false;
    return includesAny(str, [
      "sendchatstate",
      "send_chat_state",
      "sendchatstatefromcomposer",
      "typingindicatorstoredprocedure",
      "sendtypingindicator",
      "send_typing_indicator",
      "typing_indicator",
      "chatstate",
      "is_typing",
      "istyping"
    ]);
  }
  function isMessengerRealtimeTransport(urlString) {
    return urlString.includes("/ws/realtime") || urlString.includes("/ws/lightspeed") || urlString.includes("/ws/streamcontroller") || urlString.includes("/ws/rpsignaling") || urlString.includes("edge-chat.messenger.com/chat") || urlString.includes("edge-chat.facebook.com/chat");
  }
  function isMessengerTypingWrite(str, urlString) {
    if (urlString.includes("/ajax/messaging/typ.php") || urlString.includes("/ajax/chat/typ.php") || urlString.includes("/ajax/mercury/typ.php")) {
      return true;
    }
    if (!matchesPattern(str, PATTERNS.msgTyping)) return false;
    if (includesAny(str, [
      "sendtypingindicator",
      "send_typing_indicator",
      "send_typing",
      "sendchatstatefromcomposer",
      "sendchatstate",
      "send_chat_state",
      "chat_state",
      "chatstate",
      "typing_status",
      "typingindicatorstoredprocedure",
      "securetypingstate",
      "mawsecuretypingstate",
      "typingstate",
      "thread_typing",
      "orca_typing_notifications",
      "indicate_activity",
      "activity_indicator"
    ])) {
      return hasMessengerTypingContext(str);
    }
    return hasMessengerTypingContext(str) && includesAny(str, [
      "is_typing",
      "istyping",
      "typing_on",
      "typing_indicator",
      "typing_status",
      "typingstate",
      "securetypingstate",
      "mawsecuretypingstate",
      "is_composing",
      "iscomposing",
      "composing",
      "chatstate",
      "send_typing",
      "send_typing_indicator",
      "send_chat_state",
      "sendchatstate"
    ]);
  }
  function hasMessengerTypingContext(str) {
    return hasMessengerThreadContext(str) || includesAny(str, [
      "composer",
      "ls_req",
      "/ls_req",
      "issue_new_task",
      "issuenewtask"
    ]);
  }
  function isInstagramStorySeenWrite(str) {
    if (includesAny(str, [
      "polarisapireelseenmutation",
      "xdt_mark_story_reel_seen",
      "26997980659837802",
      "polarisapiforcestoryseenmutation",
      "xdt_api__v1__stories__reel__seen",
      "9647304595318258",
      "api/v1/stories/reel/seen",
      "stories/reel/seen",
      "forceseenstoryid"
    ])) {
      return true;
    }
    return includesAny(str, ["viewseenat"]) && includesAny(str, [
      "reelmediaid",
      "reelmediaownerid",
      "reelmediatakenat",
      "reelid"
    ]);
  }
  function hasInstagramStorySeenWriteIntent(str) {
    return isInstagramStorySeenWrite(str) || includesAny(str, [
      "storiesupdateseenmutation",
      "polarisstoriesseenmutation",
      "usepolarisstoriesv3seenmutation",
      "reelmediaseen",
      "storiesupdateseen",
      "seenstoriesupdatemutation",
      "xdt_mark_story_reel_seen",
      "api/v1/stories/reel/seen",
      "stories/reel/seen"
    ]) || hasTruthyField(str, [
      "mark_story_seen",
      "markstoryseen",
      "update_seen_for_reel",
      "updateseenforreel",
      "reel_seen",
      "reelseen",
      "stories_update_seen",
      "storiesupdateseen",
      "mark_story_read",
      "markstoryread"
    ]);
  }
  function isStaticAsset(url, method = "") {
    const safeMethod = !method || method === "GET" || method === "HEAD";
    if (!safeMethod) return false;
    return /\.(mp4|m4v|mov|webm|m3u8|mpd|m4s|ts|jpg|jpeg|png|webp|gif|mp3|wav|m4a|aac|css|js|mjs|woff2?)($|\?)/i.test(
      url
    ) || url.includes("static.xx.fbcdn.net/") || url.includes("video.xx.fbcdn.net/") || url.includes("/rsrc.php") || url.includes("/ajax/bootloader-endpoint/");
  }
  function isMediaAdOrPlayerRequest(str, urlString, method = "") {
    const text = `${str} ${urlString}`;
    if (isExplicitPrivacyWriteText(text, urlString)) return false;
    if (isStaticAsset(urlString, method)) return true;
    if (!["GET", "HEAD"].includes(String(method || "").toUpperCase()) && isMediaCdnUrl(urlString))
      return false;
    if (isMediaCdnUrl(urlString)) return true;
    const hasMediaContext = includesAny(text, [
      "video",
      "reel",
      "reels",
      "watch",
      "player",
      "playback",
      "playable",
      "playable_url",
      "dash",
      "dash_info",
      "manifest",
      "m3u8",
      "mpd",
      "m4s",
      "fbcdn",
      "scontent",
      "cdninstagram",
      "audio",
      "media"
    ]);
    const hasAdContext = includesAny(text, [
      "adbreak",
      "ad_break",
      "instream_ad",
      "instreamads",
      "commercial_break",
      "sponsored_video",
      "video_ad",
      "ad_client_token",
      "ad_creative",
      "ad_pod",
      "adsmanager",
      "adinterface"
    ]);
    if (!hasMediaContext && !hasAdContext) return false;
    if (isGraphQLRequest(text, urlString)) {
      const friendlyName = getFacebookGraphQLFriendlyName(text);
      if (friendlyName && includesAny(friendlyName, [
        "video",
        "watch",
        "player",
        "reel",
        "media",
        "adbreak",
        "ad_break",
        "instream",
        "ads",
        "cometufi"
      ])) {
        return true;
      }
      return hasAdContext || includesAny(text, [
        "playable_url",
        "dash_info",
        "video_versions",
        "video_dash_manifest",
        "browser_native_sd_url",
        "browser_native_hd_url",
        "ad_break",
        "adbreak",
        "watch_time",
        "player_state",
        "stream_type",
        "media_id",
        "video_id",
        "reel_media_id"
      ]);
    }
    return hasAdContext || includesAny(urlString, [
      "/video/",
      "/videos/",
      "/reel/",
      "/reels/",
      "/watch/",
      "/media/",
      "/ads/"
    ]);
  }
  function isMediaCdnUrl(urlString) {
    return urlString.includes("video.xx.fbcdn.net/") || urlString.includes("/video/") || urlString.includes("/videos/") || /\.(mp4|m4v|mov|webm|m3u8|mpd|m4s|ts)($|\?)/i.test(urlString);
  }
  function isExplicitPrivacyWriteText(str, urlString) {
    return isLegacyMessengerReadEndpoint(urlString) || hasInstagramStorySeenWriteIntent(str) || isInstagramDirectTypingWrite(str, urlString) || isInstagramDirectSeenWrite(str, urlString) || hasServerReadReceiptOrTypingCommand(str) || hasMessengerReadReceiptWriteSignal(str) || hasReadReceiptWatermarkContext(str);
  }
  function hasServerReadReceiptOrTypingCommand(str) {
    const text = stripFalseyPrivacyFields(str);
    return includesAny(text, [
      "sendreadreceipt",
      "lssendreadreceipt",
      "readreceiptmutation",
      "send_read_receipt",
      "markthreadasread",
      "mark_thread_read",
      "markthreadreadmutation",
      "lsmarkthreadread",
      "mwmarkthreadread",
      "lsupdatethreadreadwatermark",
      "lsupdatelastreadwatermark",
      "sendtypingindicator",
      "lssendtypingindicator",
      "lssendtypingindicatorstoredprocedure",
      "send_typing_indicator",
      "sendchatstatefromcomposer",
      "sendchatstate",
      "send_chat_state",
      "typingindicatorstoredprocedure"
    ]);
  }
  function isFacebookExplicitMessengerSeenWrite(str, urlString) {
    if (urlString.includes("/ajax/mercury/change_read_status.php")) return true;
    if (isMessengerRealtimeReadBridgeWrite(str, urlString)) return true;
    if (isGraphQLRequest(str, urlString)) return false;
    if (!hasStrictFacebookMessengerWriteContext(str) && !isMessengerRealtimeTransport(urlString))
      return false;
    const hasExplicitWrite = hasMessengerReadReceiptWriteSignal(str);
    if (!hasExplicitWrite) return false;
    return includesAny(str, [
      "mutation",
      "procedure",
      "storedprocedure",
      "ls_req",
      "/ls_req",
      "issue_new_task",
      "issuenewtask",
      "fb_api_req_friendly_name"
    ]) || isMessengerRealtimeTransport(urlString);
  }
  function isFacebookExplicitMessengerTypingWrite(str, urlString) {
    if (urlString.includes("/ajax/messaging/typ.php") || urlString.includes("/ajax/chat/typ.php") || urlString.includes("/ajax/mercury/typ.php")) {
      return true;
    }
    if (isGraphQLRequest(str, urlString)) return false;
    const hasExplicitWrite = includesAny(str, [
      "sendtypingindicator",
      "lssendtypingindicator",
      "lssendtypingindicatorstoredprocedure",
      "send_typing_indicator",
      "send_typing",
      "sendchatstatefromcomposer",
      "sendchatstate",
      "send_chat_state",
      "chat_state",
      "is_composing",
      "iscomposing",
      "composing",
      "typing_status",
      "typingindicatorstoredprocedure",
      "mawsecuretypingstate",
      "securetypingstate",
      "typingstate"
    ]);
    if (!hasExplicitWrite) return false;
    return hasStrictFacebookMessengerWriteContext(str) || hasMessengerThreadContext(str) || isMessengerRealtimeTransport(urlString) || includesAny(str, [
      "composer",
      "ls_req",
      "/ls_req",
      "issue_new_task",
      "issuenewtask"
    ]);
  }
  function isGraphQLRequest(str, urlString) {
    return urlString.includes("/api/graphql") || str.includes("fb_api_req_friendly_name") || str.includes("doc_id");
  }
  function isFacebookGraphQLMessengerSeenWrite(str) {
    const text = stripFalseyPrivacyFields(str);
    const hasNamedWrite = includesAny(text, [
      "markthreadasread",
      "mark_thread_read",
      "markthreadreadmutation",
      "markthreadread",
      "markread",
      "mark_read",
      "markseen",
      "mark_seen",
      "markasread",
      "mark_as_read",
      "lsmarkthreadread",
      "mwmarkthreadread",
      "lssendreadreceipt",
      "sendreadreceipt",
      "send_read_receipt",
      "readreceiptmutation",
      "readreceipt",
      "read_receipt",
      "lsupdatethreadreadwatermark",
      "lsupdatelastreadwatermark",
      "updatelastreadwatermark",
      "shouldsendreadreceipt",
      "should_send_read_receipt"
    ]) || hasTruthyField(str, [
      "shouldsendreadreceipt",
      "should_send_read_receipt",
      "sendreadreceipt",
      "send_read_receipt",
      "readreceipt",
      "read_receipt",
      "markread",
      "mark_read",
      "markseen",
      "mark_seen",
      "markasread",
      "mark_as_read"
    ]);
    const hasWatermarkWrite = includesAny(str, [
      "last_read_watermark",
      "read_watermark",
      "watermarktimestamp",
      "watermark_timestamp"
    ]) && includesAny(str, [
      "mutation",
      "ls_req",
      "/ls_req",
      "issue_new_task",
      "issuenewtask",
      "sendreadreceipt",
      "readreceipt",
      "markthread"
    ]);
    if (!hasNamedWrite && !hasWatermarkWrite) return false;
    if (isFacebookGraphQLMessengerQuery(str) && !hasExplicitMessengerReadWriteCommand(str))
      return false;
    return hasStrictFacebookMessengerWriteContext(str) || hasMessengerThreadContext(str) || hasReadReceiptWatermarkContext(str);
  }
  function isFacebookGraphQLMessengerTypingWrite(str) {
    const text = stripFalseyPrivacyFields(str);
    const hasExplicitWrite = includesAny(text, [
      "sendtypingindicator",
      "lssendtypingindicator",
      "lssendtypingindicatorstoredprocedure",
      "send_typing_indicator",
      "send_typing",
      "sendchatstatefromcomposer",
      "sendchatstate",
      "send_chat_state",
      "chat_state",
      "is_composing",
      "iscomposing",
      "composing",
      "typing_status",
      "typingindicatorstoredprocedure",
      "mawsecuretypingstate",
      "securetypingstate",
      "typingstate"
    ]) || hasTruthyField(str, [
      "istyping",
      "is_typing",
      "iscomposing",
      "is_composing",
      "typingindicator",
      "typing_indicator"
    ]);
    if (!hasExplicitWrite) return false;
    if (isFacebookGraphQLMessengerQuery(text) && !hasFacebookMessengerTypingWriteIntent(text))
      return false;
    return hasStrictFacebookMessengerWriteContext(text) || hasMessengerThreadContext(text) || includesAny(text, [
      "composer",
      "typing_indicator",
      "chatstate",
      "typingstate",
      "typing_status",
      "maw"
    ]);
  }
  function isFacebookGraphQLMessengerQuery(str) {
    const friendlyName = getFacebookGraphQLFriendlyName(str);
    const operationName = getFacebookGraphQLOperationName(str);
    if (friendlyName && friendlyName.includes("mutation") || operationName && operationName.includes("mutation"))
      return false;
    if (friendlyName && friendlyName.includes("query") && !friendlyName.includes("mutation"))
      return true;
    if (operationName && operationName.includes("query") && !operationName.includes("mutation"))
      return true;
    return includesAny(str, [
      "ebmessagemetadataquery",
      "messagehistoryquery",
      "threadlistquery",
      "messagelistquery",
      "searchmessengerquery",
      "messengerthreadquery",
      "messengerthreadlistquery",
      "messengerinboxquery",
      "messagerequestsquery",
      "messagerequestquery",
      "message_requests",
      "message request",
      "messagerequests",
      "filteredthreads",
      "filtered_threads",
      "pendingthreads",
      "pending_threads",
      "spamthreads",
      "spam_threads"
    ]);
  }
  function getFacebookGraphQLFriendlyName(str) {
    const match = String(str || "").match(/fb_api_req_friendly_name=([^&\s]+)/) || String(str || "").match(/"fb_api_req_friendly_name"\s*:\s*"([^"]+)/);
    return match ? String(match[1] || "").toLowerCase() : "";
  }
  function getFacebookGraphQLOperationName(str) {
    const match = String(str || "").match(/operationname=([^&\s]+)/) || String(str || "").match(/"operationname"\s*:\s*"([^"]+)/) || String(str || "").match(/"operation_name"\s*:\s*"([^"]+)/);
    return match ? String(match[1] || "").toLowerCase() : "";
  }
  function hasStrictFacebookMessengerWriteContext(str) {
    return includesAny(str, [
      "cometmessenger",
      "mwchat",
      "maw",
      "lsplatform",
      "thread_key",
      "threadkey",
      "thread_fbid",
      "threadfbid",
      "thread_id",
      "recipient_id",
      "message_thread",
      "act_thread_id"
    ]);
  }
  function stripInstagramUserAuthoredText(text) {
    return String(text || "").replace(
      /((?:^|[&\s"{,])(?:text|message|item_text|client_context_message|reply_text|comment_text)\s*[=:]\s*)("[^"]*"|[^&\s,}]+)/gi,
      "$1<user_text>"
    ).replace(
      /((?:^|[&\s"{,])(?:text|message|item_text|client_context_message|reply_text|comment_text)"\s*:\s*)("[^"]*"|[^,}]+)/gi,
      "$1<user_text>"
    );
  }
  function hasInstagramDirectContext(str, urlString) {
    return includesAny(`${str} ${urlString}`, [
      "direct_v2",
      "/direct/",
      "directthread",
      "direct_thread",
      "polarisdirect",
      "xdt_direct",
      "instagramdirect",
      "thread_id",
      "threadid",
      "thread_pk",
      "inbox"
    ]);
  }
  function isInstagramDirectTypingWrite(str, urlString) {
    if (includesAny(urlString, [
      "/direct_v2/threads/broadcast/typing",
      "/direct_v2/threads/typing"
    ]) || urlString.includes("/direct_v2/threads/") && urlString.includes("/typing")) {
      return true;
    }
    if (!hasInstagramDirectContext(str, urlString)) return false;
    const hasWriteContext = includesAny(str, [
      "mutation",
      "fb_api_req_friendly_name",
      "direct_v2",
      "thread_id",
      "threadid",
      "thread_pk",
      "recipient_id",
      "recipientid"
    ]);
    return hasWriteContext && includesAny(str, [
      "sendtypingindicator",
      "send_typing_indicator",
      "typingindicatorstoredprocedure",
      "directsendtyping",
      "direct_send_typing",
      "igdsendtyping",
      "typing_indicator",
      "is_typing",
      "typing_on",
      "is_composing"
    ]);
  }
  function isInstagramDirectMessageSendEndpoint(urlString) {
    return includesAny(urlString, [
      "/direct_v2/threads/broadcast/text",
      "/direct_v2/threads/broadcast/link",
      "/direct_v2/threads/broadcast/media",
      "/direct_v2/threads/broadcast/photo",
      "/direct_v2/threads/broadcast/video",
      "/direct_v2/threads/broadcast/voice",
      "/direct_v2/threads/broadcast/reel_share",
      "/direct_v2/threads/broadcast/story_share",
      "/direct_v2/threads/broadcast/profile",
      "/direct_v2/threads/broadcast/hashtag",
      "/direct_v2/threads/broadcast/location"
    ]);
  }
  function isInstagramDirectSeenWrite(str, urlString) {
    if (includesAny(urlString, [
      "/direct_v2/threads/seen",
      "/direct_v2/threads/mark_seen",
      "/direct_v2/threads/mark_read"
    ]) || urlString.includes("/direct_v2/threads/") && includesAny(urlString, ["/seen", "mark_seen", "mark_read"])) {
      return true;
    }
    if (!hasInstagramDirectContext(str, urlString)) return false;
    const hasWriteContext = includesAny(str, [
      "mutation",
      "fb_api_req_friendly_name",
      "direct_v2",
      "thread_id",
      "threadid",
      "thread_pk",
      "item_id",
      "itemid",
      "watermark",
      "timestamp"
    ]);
    return hasWriteContext && includesAny(str, [
      "directmarkasseen",
      "directthreadmarkitemsseen",
      "polarisdirectmarkasseenmutation",
      "directseenmutation",
      "usepolarismarkthreadseenmutation",
      "useigdmarkthreadasreadmutation",
      "markthreadseenmutation",
      "markthreadasreadmutation",
      "markdirectthreadseen",
      "mark_direct_thread_seen",
      "mark_seen",
      "mark_read",
      "thread_seen",
      "markasseen"
    ]);
  }
  function isInstagramDirectSafeRequest(str, urlString, method) {
    if (!hasInstagramDirectContext(str, urlString)) return false;
    if (isInstagramDirectMessageSendEndpoint(urlString)) return true;
    if (isInstagramDirectTypingWrite(str, urlString) || isInstagramDirectSeenWrite(str, urlString) || isInstagramStorySeenWrite(str)) {
      return false;
    }
    if (includesAny(urlString, [
      "/direct_v2/inbox",
      "/direct_v2/threads/",
      "/direct_v2/threads/broadcast/"
    ])) {
      return true;
    }
    if ((method === "GET" || method === "HEAD") && includesAny(urlString, ["/api/graphql", "/direct_v2/"])) {
      return true;
    }
    if (isGraphQLRequest(str, urlString) && includesAny(str, ["direct", "inbox", "thread"])) {
      return true;
    }
    return (str.includes("cursor") || urlString.includes("cursor") || str.includes("query_hash") || str.includes("doc_id")) && includesAny(str, ["direct", "inbox", "thread"]);
  }
  function isMessengerReadOnlyNavigationRequest(str, urlString, method) {
    if (isExplicitPrivacyWriteText(str, urlString)) return false;
    if ((method === "GET" || method === "HEAD") && isGraphQLRequest(str, urlString))
      return true;
    const friendlyName = getFacebookGraphQLFriendlyName(str);
    const operationName = getFacebookGraphQLOperationName(str);
    if (friendlyName && friendlyName.includes("mutation") || operationName && operationName.includes("mutation"))
      return false;
    if (isFacebookGraphQLMessengerQuery(str)) return true;
    return isGraphQLRequest(str, urlString) && includesAny(str, [
      "message_requests",
      "message request",
      "messagerequests",
      "message_request",
      "inbox",
      "mailbox",
      "threadlist",
      "thread_list",
      "messagehistory",
      "message_history",
      "messagelist",
      "message_list",
      "filteredthreads",
      "filtered_threads",
      "pendingthreads",
      "pending_threads",
      "spamthreads",
      "spam_threads",
      "folder",
      "pagination",
      "cursor"
    ]);
  }
  function shouldBlock(data, url = "", options = {}) {
    const urlString = String(url || "");
    const method = String(options.method || "").toUpperCase();
    if (isStaticAsset(urlString, method)) return null;
    if (shouldBypassNativeMessageRequestTransport(data, urlString, { method }))
      return null;
    const decodedBody = decode(data).toLowerCase();
    const str = `${decodedBody} ${urlString}`.toLowerCase();
    const isFacebookPage = isFacebookDotCom && !isMessengerDotCom;
    if (isFacebookPage) {
      if (isMessageRequestHydrationRequest(str, urlString, method))
        return null;
      if (hasMessengerMessageSendIntent(str)) return null;
      if (hasMessengerDeliveryAckIntent(str)) return null;
      if (SETTINGS.msgSeen && !isKilled("msgSeen") && isGraphQLRequest(str, urlString) && isFacebookGraphQLMessengerSeenWrite(str)) {
        return "MSG_SEEN";
      }
      if (SETTINGS.msgSeen && !isKilled("msgSeen") && isFacebookExplicitMessengerSeenWrite(str, urlString)) {
        return "MSG_SEEN";
      }
      if (isMessengerNormalThreadListPaginationTask(str)) return null;
      if (SETTINGS.msgTyping && !isKilled("msgTyping") && isGraphQLRequest(str, urlString) && isFacebookGraphQLMessengerTypingWrite(str)) {
        return "MSG_TYPING";
      }
      if (SETTINGS.msgTyping && !isKilled("msgTyping") && isFacebookExplicitMessengerTypingWrite(str, urlString)) {
        return "MSG_TYPING";
      }
      if (SETTINGS.msgStory && !isKilled("msgStory") && isFacebookMobileStorySeenWebLiteFrame(data, urlString)) {
        return "MSG_STORY";
      }
      if (SETTINGS.msgStory && !isKilled("msgStory") && hasExplicitStorySeenSignal(str) && matchesPattern(str, PATTERNS.msgStory)) {
        return "MSG_STORY";
      }
      if (isMediaAdOrPlayerRequest(str, urlString, method)) return null;
      if (isMessengerReadOnlyNavigationRequest(str, urlString, method))
        return null;
      return null;
    }
    if (isMessenger) {
      if (hasMessengerMessageSendIntent(str)) return null;
      if (hasMessengerDeliveryAckIntent(str)) return null;
      if (isMessageRequestHydrationRequest(str, urlString, method))
        return null;
      if (SETTINGS.msgSeen && !isKilled("msgSeen")) {
        if (isMessengerReadReceiptWrite(str, urlString)) {
          return "MSG_SEEN";
        }
      }
      if (str.includes("delivery_receipt") && !hasMessengerReadReceiptSignal(str))
        return null;
      if (SETTINGS.msgTyping && !isKilled("msgTyping") && (!isFacebookPage || hasFacebookMessengerContext(str)) && isMessengerTypingWrite(str, urlString)) {
        return "MSG_TYPING";
      }
      if (SETTINGS.msgStory && !isKilled("msgStory") && (!isFacebookPage || hasExplicitStorySeenSignal(str)) && matchesPattern(str, PATTERNS.msgStory)) {
        return "MSG_STORY";
      }
      if (isMediaAdOrPlayerRequest(str, urlString, method)) return null;
      if (isMessengerReadOnlyNavigationRequest(str, urlString, method))
        return null;
      return null;
    }
    if (isInstagram) {
      const instagramMatchText = `${stripInstagramUserAuthoredText(decodedBody)} ${urlString}`.toLowerCase();
      const storyViewerLookup = isInstagramStoryViewerLookup(instagramMatchText);
      if (isInstagramDirectMessageSendEndpoint(urlString)) return null;
      if (SETTINGS.igTyping && !isKilled("igTyping") && isInstagramDirectTypingWrite(instagramMatchText, urlString)) {
        return "IG_TYPING";
      }
      if (SETTINGS.igStory && !isKilled("igStory") && isInstagramStorySeenWrite(instagramMatchText)) {
        return "IG_STORY";
      }
      if (SETTINGS.igSeen && !isKilled("igSeen") && isInstagramDirectSeenWrite(instagramMatchText, urlString)) {
        return "IG_SEEN";
      }
      if (isMediaAdOrPlayerRequest(instagramMatchText, urlString, method))
        return null;
      if (instagramMatchText.includes("cursor") || urlString.includes("cursor") || instagramMatchText.includes("query_hash") || instagramMatchText.includes("doc_id")) {
        const hasFallbackPrivacyPattern = SETTINGS.igStory && !isKilled("igStory") && !storyViewerLookup && matchesPattern(instagramMatchText, PATTERNS.igStory) || SETTINGS.igTyping && !isKilled("igTyping") && matchesPattern(instagramMatchText, PATTERNS.igTyping) || SETTINGS.igSeen && !isKilled("igSeen") && matchesPattern(instagramMatchText, PATTERNS.igSeen);
        if (!hasFallbackPrivacyPattern) return null;
      }
      if (isInstagramDirectSafeRequest(instagramMatchText, urlString, method))
        return null;
      if (SETTINGS.igStory && !isKilled("igStory") && !storyViewerLookup && matchesPattern(instagramMatchText, PATTERNS.igStory)) {
        return "IG_STORY";
      }
      if (SETTINGS.igTyping && !isKilled("igTyping") && matchesPattern(instagramMatchText, PATTERNS.igTyping)) {
        return "IG_TYPING";
      }
      if (SETTINGS.igSeen && !isKilled("igSeen") && matchesPattern(instagramMatchText, PATTERNS.igSeen)) {
        return "IG_SEEN";
      }
      if (instagramMatchText.includes("cursor") || urlString.includes("cursor"))
        return null;
      if (instagramMatchText.includes("query_hash")) return null;
      if (instagramMatchText.includes("doc_id")) return null;
      return null;
    }
    return null;
  }
  function isNativeMessageRequestBypassActive() {
    try {
      if (typeof window === "undefined") return false;
      return Number(window.__GHOSTIFY_MESSAGE_REQUEST_NATIVE_UNTIL__ || 0) > Date.now();
    } catch (e) {
      return false;
    }
  }
  function shouldBypassNativeMessageRequestTransport(data, url = "", options = {}) {
    if (!isNativeMessageRequestBypassActive()) return false;
    const urlString = String(url || "").toLowerCase();
    const method = String(options.method || "").toUpperCase();
    const decodedBody = decode(data).toLowerCase();
    const str = `${decodedBody} ${urlString}`;
    return isMessageRequestHydrationRequest(str, urlString, method);
  }

  // src/utils/diagnostic-version.js
  var DIAGNOSTIC_VERSION = "2026-05-23-instagram-direct-27";

  // src/utils/debug.js
  var DEBUG_TERMS = [
    "api/graphql",
    "stories",
    "story",
    "reel",
    "seen",
    "direct_v2",
    "typing",
    "indicate_activity",
    "sendtyping",
    "sendchatstate",
    "typ.php",
    "mqtt",
    "edge-chat"
  ];
  var PAGE_HASH_SALT = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  var PAGE_START_MS = Date.now();
  var MESSENGER_OBSERVE_TERMS = [
    "sendtypingindicator",
    "lssendtypingindicator",
    "lssendtypingindicatorstoredprocedure",
    "sendchatstate",
    "send_chat_state",
    "sendchatstatefromcomposer",
    "mawsecuretypingstate",
    "securetypingstate",
    "typingindicatorstoredprocedure",
    "is_typing",
    "istyping",
    "typing_indicator",
    "typing_on",
    "send_typing",
    "send_typing_indicator",
    "thread_typing",
    "orca_typing_notifications",
    "indicate_activity",
    "activity_indicator",
    "composer",
    "markthreadasread",
    "mark_thread_read",
    "markthreadreadmutation",
    "markthreadread",
    "markread",
    "markasread",
    "mark_read",
    "markseen",
    "mark_seen",
    "thread_seen",
    "threadseen",
    "change_read_status",
    "updatelastseenat",
    "updatelastreadwatermark",
    "sendreadreceipt",
    "lssendreadreceipt",
    "readreceiptmutation",
    "readreceipt",
    "lsupdatethreadreadwatermark",
    "lsmarkthreadread",
    "mwmarkthreadread",
    "lsupdatelastreadwatermark",
    "last_read_watermark",
    "last_read_watermark_ts",
    "read_watermark",
    "watermarktimestamp",
    "watermark_timestamp",
    "shouldsendreadreceipt",
    "seenbyviewer",
    "seen_by_viewer",
    "read_receipt",
    "delivery_receipt",
    "deliveryreceipt",
    "delivery_receipts",
    "message_delivered",
    "markdelivered",
    "ls_req",
    "issue_new_task",
    "issuenewtask",
    "thread_key",
    "thread_fbid",
    "act_thread_id",
    "api/graphql",
    "ajax/messaging/typ.php",
    "ajax/chat/typ.php",
    "ajax/mercury/change_read_status.php",
    "mqtt",
    "edge-chat"
  ];
  function markGhostifyHook(name, details = {}) {
    var _a, _b;
    try {
      installDebugHelpers();
      const status = window.__GHOSTIFY_STATUS__ || {
        version: DIAGNOSTIC_VERSION,
        host: window.location.hostname,
        hrefPath: "<redacted>",
        startedAt: roundedElapsedSeconds(),
        hooks: {}
      };
      status.version = DIAGNOSTIC_VERSION;
      status.host = window.location.hostname;
      status.debug = {
        ghostifyDebug: ((_a = window.localStorage) == null ? void 0 : _a.ghostifyDebug) === "1",
        ghostifyMessengerObserve: ((_b = window.localStorage) == null ? void 0 : _b.ghostifyMessengerObserve) === "1"
      };
      status.hooks[name] = Object.assign(
        { t: roundedElapsedSeconds() },
        details
      );
      window.__GHOSTIFY_STATUS__ = status;
    } catch (e) {
    }
  }
  function traceNetwork(kind, url, body, blockType = null) {
    if (!isDebugEnabled()) return;
    const urlString = String(url || "");
    const bodyText = summarizeBody(body);
    const haystack = `${urlString} ${bodyText}`.toLowerCase();
    const terms = DEBUG_TERMS.filter((term) => haystack.includes(term));
    if (!blockType && !terms.length) return;
    const event = {
      t: roundedElapsedSeconds(),
      kind,
      blockType,
      url: redactUrl(urlString),
      terms
    };
    const events = window.__GHOSTIFY_DEBUG_EVENTS__ || [];
    events.push(event);
    window.__GHOSTIFY_DEBUG_EVENTS__ = events.slice(-200);
    try {
      console.debug("[Ghostify]", event);
    } catch (e) {
    }
  }
  function traceMessengerObservation(kind, url, body, blockType = null) {
    if (!isMessengerObservationEnabled()) return;
    const urlString = String(url || "");
    const bodyText = summarizeBody(body, 6e3);
    const haystack = `${urlString} ${bodyText}`.toLowerCase();
    const terms = MESSENGER_OBSERVE_TERMS.filter(
      (term) => haystack.includes(term)
    );
    const nearMiss = isMessengerNearMiss(kind, urlString, haystack);
    if (!blockType && !terms.length && !nearMiss) return;
    const dataShape = describeDataShape(body, bodyText || urlString);
    const phase = getCapturePhase();
    if (shouldThrottleNearMiss(
      kind,
      urlString,
      blockType,
      terms,
      dataShape,
      phase
    ))
      return;
    const event = {
      v: 1,
      t: roundedElapsedSeconds(),
      phase,
      transport: kind,
      action: blockType ? "drop" : terms.length ? "allow" : "near_miss",
      blockType,
      featureGuess: guessFeature(haystack, blockType),
      url: redactUrl(urlString),
      terms,
      flags: makeObservationFlags(haystack),
      request: extractRequestMetadata(urlString, bodyText),
      dataShape,
      callSite: getCallSite(),
      redaction: {
        rawStored: false,
        idsHashed: true,
        pageSalted: true
      }
    };
    pushObservation(event);
    try {
      console.debug("[Ghostify Messenger Observe]", event);
    } catch (e) {
    }
  }
  function traceMessengerHealth(source, details = {}) {
    markGhostifyHook(source, details);
    if (!isMessengerObservationEnabled()) return;
    const event = {
      v: 1,
      t: roundedElapsedSeconds(),
      phase: getCapturePhase(),
      transport: "health",
      action: "hook",
      blockType: null,
      featureGuess: "health",
      source,
      details: sanitizeDetails(details),
      redaction: {
        rawStored: false,
        idsHashed: true,
        pageSalted: true
      }
    };
    pushObservation(event);
    try {
      console.debug("[Ghostify Messenger Observe]", event);
    } catch (e) {
    }
  }
  function installDebugHelpers() {
    try {
      if (window.__GHOSTIFY_CAPTURE_HELPERS__) return;
      window.__GHOSTIFY_CAPTURE_HELPERS__ = DIAGNOSTIC_VERSION;
      window.__GHOSTIFY_OBSERVATION_COUNTS__ = window.__GHOSTIFY_OBSERVATION_COUNTS__ || {};
      window.__GHOSTIFY_RESET_CAPTURE__ = function(phase = "baseline") {
        window.__GHOSTIFY_CAPTURE_PHASE__ = String(
          phase || "baseline"
        ).slice(0, 40);
        window.__GHOSTIFY_MESSENGER_OBSERVATIONS__ = [];
        window.__GHOSTIFY_DEBUG_EVENTS__ = [];
        window.__GHOSTIFY_OBSERVATION_COUNTS__ = {};
        window.__GHOSTIFY_BLOCKED_WORKER_MESSAGES__ = 0;
        window.__GHOSTIFY_SANITIZED_WORKER_MESSAGES__ = 0;
        window.__GHOSTIFY_SANITIZED_SEEN_BRIDGE_MESSAGES__ = 0;
        window.__GHOSTIFY_SANITIZED_NETWORK_MESSAGES__ = 0;
        window.__GHOSTIFY_FACEBOOK_UNSAFE_BLOCKS_SKIPPED__ = 0;
        window.__GHOSTIFY_MESSENGER_UNSAFE_BLOCKS_SKIPPED__ = 0;
        window.__GHOSTIFY_UNSAFE_TRANSFER_BLOCKS_SKIPPED__ = 0;
        window.__GHOSTIFY_BLOCKED_TYPING_EXPORT_CALLS__ = 0;
        window.__GHOSTIFY_BLOCKED_READ_EXPORT_CALLS__ = 0;
        window.__GHOSTIFY_SANITIZED_READ_EXPORT_CALLS__ = 0;
        pushObservation(
          createMarkerEvent(`reset:${window.__GHOSTIFY_CAPTURE_PHASE__}`)
        );
        return `Ghostify capture reset: ${window.__GHOSTIFY_CAPTURE_PHASE__}`;
      };
      window.__GHOSTIFY_MARK__ = function(phase) {
        window.__GHOSTIFY_CAPTURE_PHASE__ = String(phase || "mark").slice(
          0,
          40
        );
        pushObservation(
          createMarkerEvent(window.__GHOSTIFY_CAPTURE_PHASE__)
        );
        return `Ghostify phase: ${window.__GHOSTIFY_CAPTURE_PHASE__}`;
      };
      window.__GHOSTIFY_REPORT__ = function() {
        return JSON.stringify(
          {
            status: window.__GHOSTIFY_STATUS__,
            phase: getCapturePhase(),
            observations: window.__GHOSTIFY_MESSENGER_OBSERVATIONS__ || [],
            observationCounts: window.__GHOSTIFY_OBSERVATION_COUNTS__ || {},
            debugEvents: window.__GHOSTIFY_DEBUG_EVENTS__ || [],
            blockedWorkerMessages: window.__GHOSTIFY_BLOCKED_WORKER_MESSAGES__ || 0,
            sanitizedWorkerMessages: window.__GHOSTIFY_SANITIZED_WORKER_MESSAGES__ || 0,
            sanitizedSeenBridgeMessages: window.__GHOSTIFY_SANITIZED_SEEN_BRIDGE_MESSAGES__ || 0,
            sanitizedNetworkMessages: window.__GHOSTIFY_SANITIZED_NETWORK_MESSAGES__ || 0,
            facebookUnsafeBlocksSkipped: window.__GHOSTIFY_FACEBOOK_UNSAFE_BLOCKS_SKIPPED__ || 0,
            messengerUnsafeBlocksSkipped: window.__GHOSTIFY_MESSENGER_UNSAFE_BLOCKS_SKIPPED__ || 0,
            unsafeTransferBlocksSkipped: window.__GHOSTIFY_UNSAFE_TRANSFER_BLOCKS_SKIPPED__ || 0,
            blockedTypingExportCalls: window.__GHOSTIFY_BLOCKED_TYPING_EXPORT_CALLS__ || 0,
            blockedReadExportCalls: window.__GHOSTIFY_BLOCKED_READ_EXPORT_CALLS__ || 0,
            sanitizedReadExportCalls: window.__GHOSTIFY_SANITIZED_READ_EXPORT_CALLS__ || 0,
            settings: window.__GHOSTIFY_SETTINGS__
          },
          null,
          2
        );
      };
    } catch (e) {
    }
  }
  function createMarkerEvent(label) {
    return {
      v: 1,
      t: roundedElapsedSeconds(),
      phase: getCapturePhase(),
      transport: "marker",
      action: "mark",
      blockType: null,
      featureGuess: "marker",
      label: String(label || "").slice(0, 80),
      redaction: {
        rawStored: false,
        idsHashed: true,
        pageSalted: true
      }
    };
  }
  function getCapturePhase() {
    try {
      return String(window.__GHOSTIFY_CAPTURE_PHASE__ || "unmarked").slice(
        0,
        40
      );
    } catch (e) {
      return "unmarked";
    }
  }
  function pushObservation(event) {
    try {
      const events = window.__GHOSTIFY_MESSENGER_OBSERVATIONS__ || [];
      events.push(event);
      window.__GHOSTIFY_MESSENGER_OBSERVATIONS__ = events.slice(-200);
    } catch (e) {
    }
  }
  function shouldThrottleNearMiss(kind, urlString, blockType, terms, dataShape, phase) {
    if (blockType || terms.length) return false;
    const key = [
      phase || "unmarked",
      kind,
      safeEndpointClass(urlString),
      dataShape.kind,
      dataShape.approxBytes
    ].join("|");
    const counts = window.__GHOSTIFY_OBSERVATION_COUNTS__ || {};
    counts[key] = (counts[key] || 0) + 1;
    window.__GHOSTIFY_OBSERVATION_COUNTS__ = counts;
    return counts[key] > 5;
  }
  function safeEndpointClass(urlString) {
    try {
      const url = new URL(String(urlString || ""), window.location.href);
      return `${url.hostname}${url.pathname}`;
    } catch (e) {
      return `url:${hashText(urlString)}`;
    }
  }
  function isDebugEnabled() {
    var _a;
    try {
      return ((_a = window.localStorage) == null ? void 0 : _a.ghostifyDebug) === "1";
    } catch (e) {
      return false;
    }
  }
  function isMessengerObservationEnabled() {
    var _a, _b;
    try {
      const host = window.location.hostname.toLowerCase();
      const supportedHost = host === "messenger.com" || host.endsWith(".messenger.com") || host === "facebook.com" || host.endsWith(".facebook.com") || host === "fbsbx.com" || host.endsWith(".fbsbx.com");
      return supportedHost && ((_a = window.localStorage) == null ? void 0 : _a.ghostifyDebug) === "1" && ((_b = window.localStorage) == null ? void 0 : _b.ghostifyMessengerObserve) === "1";
    } catch (e) {
      return false;
    }
  }
  function isMessengerNearMiss(kind, urlString, haystack) {
    if (!isMessengerHost()) return false;
    if (kind === "websocket") return true;
    const safeUrl = String(urlString || "").toLowerCase();
    return safeUrl.includes("/api/graphql") || safeUrl.includes("/ajax/") || safeUrl.includes("/ls_req") || haystack.includes("fb_api_req_friendly_name") || haystack.includes("doc_id") || haystack.includes("comet") || haystack.includes("messenger") || haystack.includes("maw") || haystack.includes("lsplatform") || haystack.includes("mwchat");
  }
  function isMessengerHost() {
    try {
      const host = window.location.hostname.toLowerCase();
      return host === "messenger.com" || host.endsWith(".messenger.com") || host === "facebook.com" || host.endsWith(".facebook.com") || host === "fbsbx.com" || host.endsWith(".fbsbx.com");
    } catch (e) {
      return false;
    }
  }
  function summarizeBody(body, limit = 2e3) {
    try {
      if (!body) return "";
      if (typeof body === "string") return withDecodedText2(body, limit);
      if (body instanceof URLSearchParams)
        return withDecodedText2(body.toString(), limit);
      if (body instanceof ArrayBuffer)
        return decodeBytes(new Uint8Array(body.slice(0, limit)), limit);
      if (ArrayBuffer.isView(body)) {
        return decodeBytes(
          new Uint8Array(
            body.buffer,
            body.byteOffset,
            Math.min(body.byteLength, limit)
          ),
          limit
        );
      }
      if (body instanceof FormData) {
        let text = "";
        for (const [key, value] of body.entries()) {
          text += `${key}=${typeof value === "string" ? value : "[file]"}&`;
          if (text.length >= limit) break;
        }
        return withDecodedText2(text, limit);
      }
    } catch (e) {
    }
    return "";
  }
  function withDecodedText2(value, limit) {
    const raw = String(value || "").slice(0, limit);
    try {
      const decoded = decodeURIComponent(raw.replace(/\+/g, " "));
      return decoded && decoded !== raw ? `${raw} ${decoded}`.slice(0, limit * 2) : raw;
    } catch (e) {
      return raw;
    }
  }
  function decodeBytes(bytes, limit) {
    try {
      return withDecodedText2(new TextDecoder().decode(bytes), limit);
    } catch (e) {
      return "";
    }
  }
  function guessFeature(haystack, blockType) {
    if (blockType) return blockType;
    if (haystack.includes("delivery_receipt")) return "delivery";
    if (haystack.includes("typing") || haystack.includes("sendchatstate") || haystack.includes("typ.php")) {
      return "typing";
    }
    if (haystack.includes("readreceipt") || haystack.includes("read_receipt") || haystack.includes("read_watermark") || haystack.includes("markthreadasread") || haystack.includes("markasread") || haystack.includes("mark_read") || haystack.includes("mark_seen") || haystack.includes("thread_seen")) {
      return "seen";
    }
    return "unknown";
  }
  function makeObservationFlags(haystack) {
    return {
      hasGraphQL: haystack.includes("api/graphql") || haystack.includes("doc_id"),
      hasLSRequest: haystack.includes("ls_req") || haystack.includes("issue_new_task") || haystack.includes("issuenewtask"),
      hasThreadTarget: haystack.includes("thread_key") || haystack.includes("threadkey") || haystack.includes("thread_fbid") || haystack.includes("threadfbid") || haystack.includes("thread_id") || haystack.includes("threadid") || haystack.includes("recipient_id") || haystack.includes("message_thread") || haystack.includes("act_thread_id"),
      hasWatermark: haystack.includes("read_watermark") || haystack.includes("readwatermark") || haystack.includes("last_read_watermark") || haystack.includes("lastreadwatermark") || haystack.includes("watermarktimestamp"),
      hasTypingState: haystack.includes("is_typing") || haystack.includes("istyping") || haystack.includes("typing_indicator") || haystack.includes("typing_status") || haystack.includes("typing_on") || haystack.includes("sendchatstate") || haystack.includes("chatstate"),
      hasDeliveryReceipt: haystack.includes("delivery_receipt"),
      hasReadReceipt: haystack.includes("readreceipt") || haystack.includes("read_receipt") || haystack.includes("sendreadreceipt") || haystack.includes("markthreadasread"),
      hasLegacyTypingEndpoint: haystack.includes("ajax/messaging/typ.php") || haystack.includes("ajax/chat/typ.php"),
      hasLegacyReadEndpoint: haystack.includes(
        "ajax/mercury/change_read_status.php"
      )
    };
  }
  function extractRequestMetadata(urlString, bodyText) {
    const metadata = {};
    try {
      const url = new URL(urlString, window.location.href);
      metadata.path = url.pathname;
      assignMetadataValue(
        metadata,
        "fb_api_req_friendly_name",
        url.searchParams.get("fb_api_req_friendly_name")
      );
      assignMetadataValue(metadata, "doc_id", url.searchParams.get("doc_id"));
    } catch (e) {
    }
    for (const key of ["fb_api_req_friendly_name", "doc_id"]) {
      const value = extractParam(bodyText, key);
      assignMetadataValue(metadata, key, value);
    }
    return metadata;
  }
  function extractParam(text, key) {
    try {
      const match = String(text || "").match(
        new RegExp(`${key}=([^&\\s]+)`, "i")
      );
      return match ? decodeURIComponent(match[1].replace(/\+/g, " ")) : "";
    } catch (e) {
      return "";
    }
  }
  function describeBody(body) {
    if (!body) return "empty";
    if (typeof body === "string") return "string";
    if (body instanceof URLSearchParams) return "urlsearchparams";
    if (body instanceof FormData) return "formdata";
    if (body instanceof ArrayBuffer) return "arraybuffer";
    if (ArrayBuffer.isView(body)) return "typedarray";
    if (typeof body === "object") return "object";
    return typeof body;
  }
  function describeDataShape(body, hashSource) {
    const shape = {
      kind: describeBody(body),
      approxBytes: estimateLength(body, String(hashSource || "")),
      hash: hashText(hashSource || "")
    };
    try {
      if (body instanceof ArrayBuffer) {
        addBinaryShape(shape, new Uint8Array(body));
      } else if (ArrayBuffer.isView(body)) {
        addBinaryShape(
          shape,
          new Uint8Array(body.buffer, body.byteOffset, body.byteLength)
        );
      } else if (Array.isArray(body)) {
        shape.arrayLength = body.length;
        shape.itemKinds = body.slice(0, 8).map(describeBody);
      } else if (body && typeof body === "object" && !(body instanceof URLSearchParams) && !(body instanceof FormData)) {
        let keyCount = 0;
        const keyHashes = [];
        for (const key of Object.keys(body)) {
          keyCount += 1;
          if (keyHashes.length < 8) keyHashes.push(hashText(key));
        }
        shape.keyCount = keyCount;
        shape.keyHashes = keyHashes;
      }
    } catch (e) {
    }
    return shape;
  }
  function addBinaryShape(shape, bytes) {
    shape.byteLength = bytes.byteLength;
    shape.prefix8Hash = hashBytes(bytes, 8);
    shape.prefix32Hash = hashBytes(bytes, 32);
  }
  function hashBytes(bytes, limit) {
    let text = "";
    const length = Math.min(bytes.byteLength, limit);
    for (let i = 0; i < length; i++) {
      text += String.fromCharCode(bytes[i]);
    }
    return hashText(text);
  }
  function estimateLength(body, bodyText) {
    try {
      if (!body) return 0;
      if (typeof body === "string") return body.length;
      if (body instanceof URLSearchParams) return body.toString().length;
      if (body instanceof ArrayBuffer) return body.byteLength;
      if (ArrayBuffer.isView(body)) return body.byteLength;
      return bodyText.length;
    } catch (e) {
      return 0;
    }
  }
  function getCallSite() {
    try {
      const stack = new Error().stack;
      if (!stack) return [];
      return stack.split("\n").slice(3, 8).map(sanitizeStackLine).filter(Boolean);
    } catch (e) {
      return [];
    }
  }
  function sanitizeStackLine(line) {
    const value = String(line || "").trim();
    if (!value) return "";
    return value.replace(/https?:\/\/[^\s)]+/g, (match) => {
      try {
        const url = new URL(match);
        const file = url.pathname.split("/").filter(Boolean).pop() || url.hostname;
        return `${url.hostname}/${file}`;
      } catch (e) {
        return `url:${hashText(match)}`;
      }
    }).slice(0, 180);
  }
  function hashText(text) {
    const input = `${PAGE_HASH_SALT}:${String(text || "")}`;
    let hash = 2166136261;
    for (let i = 0; i < input.length; i++) {
      hash ^= input.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0).toString(16).padStart(8, "0");
  }
  function redactMetadataValue(key, value) {
    const normalized = String(value || "").slice(0, 160);
    if (key === "fb_api_req_friendly_name" && /^[A-Za-z0-9_.$:-]{1,120}$/.test(normalized)) {
      return normalized;
    }
    return `hash:${hashText(normalized)}`;
  }
  function sanitizeDetails(details) {
    const output = {};
    if (!details || typeof details !== "object") return output;
    for (const [key, value] of Object.entries(details)) {
      if (value == null || typeof value === "boolean" || typeof value === "number") {
        output[key] = value;
      } else {
        output[key] = String(value).slice(0, 80);
      }
    }
    return output;
  }
  function assignMetadataValue(metadata, key, value) {
    if (!value) return;
    if (key === "doc_id") {
      if (!metadata.doc_id_hash)
        metadata.doc_id_hash = redactMetadataValue(key, value);
      return;
    }
    if (!metadata[key]) metadata[key] = redactMetadataValue(key, value);
  }
  function roundedElapsedSeconds() {
    return Math.round((Date.now() - PAGE_START_MS) / 1e3);
  }
  function redactUrl(urlString) {
    try {
      const url = new URL(urlString, window.location.href);
      const keptParams = new URLSearchParams();
      const friendlyName = url.searchParams.get("fb_api_req_friendly_name");
      const docId = url.searchParams.get("doc_id");
      if (friendlyName)
        keptParams.set(
          "fb_api_req_friendly_name",
          redactMetadataValue("fb_api_req_friendly_name", friendlyName)
        );
      if (docId)
        keptParams.set("doc_id_hash", redactMetadataValue("doc_id", docId));
      const query = keptParams.toString();
      return `${url.origin}${url.pathname}${query ? `?${query}` : ""}`;
    } catch (e) {
      return `unparseable-url:${hashText(urlString)}`;
    }
  }

  // src/core/interceptors/websocket.js
  function hookWebSocket() {
    var _a;
    if (window.__GHOSTIFY_WEBSOCKET_HOOKED__) return;
    window.__GHOSTIFY_WEBSOCKET_HOOKED__ = true;
    const OriginalWebSocket = window.WebSocket;
    const originalPrototypeSend = (_a = OriginalWebSocket == null ? void 0 : OriginalWebSocket.prototype) == null ? void 0 : _a.send;
    const socketUrls = /* @__PURE__ */ new WeakMap();
    markGhostifyHook("websocket.install", {
      hasWebSocket: typeof OriginalWebSocket === "function"
    });
    if (typeof OriginalWebSocket !== "function") return;
    function inspectSend(data, url) {
      const sanitized = sanitizeMessengerNetworkPayload(data, url);
      const inspectData = sanitized.changed ? sanitized.data : data;
      const blockType = shouldBlock(inspectData, url);
      traceNetwork("websocket", url, inspectData, blockType);
      traceMessengerObservation("websocket", url, inspectData, blockType);
      return {
        data: inspectData,
        drop: isMessengerDotCom ? blockType === "MSG_SEEN" || blockType === "MSG_TYPING" : !!blockType
      };
    }
    if (typeof originalPrototypeSend === "function") {
      OriginalWebSocket.prototype.send = function(data) {
        const socketUrl = socketUrls.get(this) || this.url || "";
        if (shouldBypassNativeMessageRequestTransport(data, socketUrl)) {
          return originalPrototypeSend.apply(this, arguments);
        }
        const inspected = inspectSend(data, socketUrl);
        if (inspected.drop) return;
        if (inspected.data !== data)
          return originalPrototypeSend.call(this, inspected.data);
        return originalPrototypeSend.apply(this, arguments);
      };
      try {
        Object.defineProperty(
          OriginalWebSocket.prototype.send,
          "__ghostifyWebSocketSendWrapped",
          {
            value: true,
            configurable: true
          }
        );
      } catch (e) {
      }
    }
    if (isMessengerDotCom) {
      markGhostifyHook("websocket.hooked", {
        messengerDotCom: true,
        prototypeSend: typeof originalPrototypeSend === "function",
        constructorProxy: false
      });
      return;
    }
    const WebSocketProxy = new Proxy(OriginalWebSocket, {
      construct(target, args, newTarget) {
        const ws = Reflect.construct(target, args, newTarget);
        socketUrls.set(ws, String(args[0] || ""));
        return ws;
      },
      apply(target, thisArg, args) {
        const ws = Reflect.apply(target, thisArg, args);
        try {
          socketUrls.set(ws, String(args[0] || ""));
        } catch (e) {
        }
        return ws;
      }
    });
    window.WebSocket = WebSocketProxy;
    markGhostifyHook("websocket.hooked", {
      messengerDotCom: isMessengerDotCom,
      prototypeSend: typeof originalPrototypeSend === "function",
      constructorProxy: true
    });
  }

  // src/utils/responses.js
  function createBlockedPayload(blockType, url, body) {
    const decoded = decode(body).toLowerCase();
    const isGraphQL = String(url || "").includes("/api/graphql") || decoded.includes("fb_api_req_friendly_name") || decoded.includes("doc_id");
    if (!isGraphQL) {
      return { status: "ok", blocked: blockType };
    }
    if (String(url || "").includes("facebook.com/api/graphql") && (blockType === "MSG_SEEN" || blockType === "MSG_TYPING")) {
      return { data: {} };
    }
    if (blockType === "IG_STORY") {
      if (decoded.includes("xdt_api__v1__stories__reel__seen") || decoded.includes("polarisapiforcestoryseenmutation") || decoded.includes("9647304595318258")) {
        return {
          data: {
            xdt_api__v1__stories__reel__seen: {
              __typename: "XDTEmptyRecord"
            }
          }
        };
      }
      return {
        data: {
          xdt_mark_story_reel_seen: {
            __typename: "XDTMarkSeenResponse"
          }
        }
      };
    }
    return { data: {} };
  }

  // src/core/interceptors/fetch.js
  function hookFetch() {
    if (window.__GHOSTIFY_FETCH_HOOKED__) return;
    window.__GHOSTIFY_FETCH_HOOKED__ = true;
    const originalFetch = window.fetch;
    markGhostifyHook("fetch.install", {
      hasFetch: typeof originalFetch === "function"
    });
    window.fetch = async function(input, init) {
      const url = getFetchUrl(input);
      const method = getFetchMethod(input, init);
      const earlyBody = init && init.body !== void 0 ? init.body : "";
      if (shouldBypassNativeMessageRequestTransport(earlyBody, url, {
        method
      })) {
        return originalFetch.apply(this, arguments);
      }
      const body = await getFetchBody(input, init);
      const sanitized = sanitizeMessengerNetworkPayload(body, url, {
        method
      });
      const inspectBody = sanitized.changed ? sanitized.data : body;
      const blockType = shouldBlock(inspectBody, url, { method });
      traceNetwork("fetch", url, inspectBody, blockType);
      traceMessengerObservation("fetch", url, inspectBody, blockType);
      if (blockType) {
        return createBlockedFetchResponse(blockType, url, inspectBody);
      }
      if (sanitized.changed) {
        if (init && init.body !== void 0) {
          return originalFetch.call(this, input, {
            ...init,
            body: sanitized.data
          });
        }
        if (typeof Request !== "undefined" && input instanceof Request) {
          return originalFetch.call(
            this,
            cloneRequestWithBody(input, sanitized.data)
          );
        }
      }
      return originalFetch.apply(this, arguments);
    };
    const originalBeacon = navigator.sendBeacon;
    if (typeof originalBeacon === "function") {
      navigator.sendBeacon = function(url, data) {
        const fetchUrl = getFetchUrl(url);
        if (shouldBypassNativeMessageRequestTransport(data, fetchUrl, {
          method: "POST"
        })) {
          return originalBeacon.apply(this, arguments);
        }
        const sanitized = sanitizeMessengerNetworkPayload(data, fetchUrl, {
          method: "POST"
        });
        const inspectData = sanitized.changed ? sanitized.data : data;
        const blockType = shouldBlock(inspectData, fetchUrl, {
          method: "POST"
        });
        traceNetwork("beacon", fetchUrl, inspectData, blockType);
        traceMessengerObservation(
          "beacon",
          fetchUrl,
          inspectData,
          blockType
        );
        if (blockType) return true;
        if (sanitized.changed)
          return originalBeacon.call(this, url, sanitized.data);
        return originalBeacon.apply(this, arguments);
      };
    }
    markGhostifyHook("fetch.hooked", {
      hasBeacon: typeof originalBeacon === "function"
    });
  }
  function getFetchMethod(input, init) {
    if (init && typeof init.method === "string") return init.method;
    if (typeof Request !== "undefined" && input instanceof Request && input.method)
      return input.method;
    if (input && typeof input.method === "string") return input.method;
    return "GET";
  }
  function getFetchUrl(input) {
    if (typeof input === "string") return input;
    if (typeof URL !== "undefined" && input instanceof URL) return input.href;
    if (typeof Request !== "undefined" && input instanceof Request)
      return input.url;
    if (input && typeof input.url === "string") return input.url;
    return String(input || "");
  }
  async function getFetchBody(input, init) {
    if (init && init.body !== void 0 && init.body !== null) {
      return readBody(init.body);
    }
    if (typeof Request !== "undefined" && input instanceof Request) {
      try {
        return await input.clone().text();
      } catch (e) {
        return "";
      }
    }
    return "";
  }
  async function readBody(body) {
    if (typeof Blob !== "undefined" && body instanceof Blob) {
      try {
        return await body.text();
      } catch (e) {
        return "";
      }
    }
    return body;
  }
  function cloneRequestWithBody(request, body) {
    try {
      return new Request(request, { body });
    } catch (e) {
      try {
        return new Request(request.url, {
          method: request.method,
          headers: request.headers,
          body,
          mode: request.mode,
          credentials: request.credentials,
          cache: request.cache,
          redirect: request.redirect,
          referrer: request.referrer,
          integrity: request.integrity,
          keepalive: request.keepalive
        });
      } catch (err) {
        return request;
      }
    }
  }
  function createBlockedFetchResponse(blockType, url, body) {
    return createJsonResponse(createBlockedPayload(blockType, url, body));
  }
  function createJsonResponse(payload) {
    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  }

  // src/core/interceptors/xhr.js
  function hookXHR() {
    if (window.__GHOSTIFY_XHR_HOOKED__) return;
    window.__GHOSTIFY_XHR_HOOKED__ = true;
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;
    markGhostifyHook("xhr.install", {
      hasOpen: typeof originalXhrOpen === "function",
      hasSend: typeof originalXhrSend === "function"
    });
    XMLHttpRequest.prototype.open = function(method, url, async) {
      this._ghostifyMethod = method || "GET";
      this._ghostifyUrl = url;
      this._ghostifyAsync = async !== false;
      return originalXhrOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function(body) {
      const url = this._ghostifyUrl || "";
      const method = this._ghostifyMethod || "GET";
      if (shouldBypassNativeMessageRequestTransport(body, url, { method })) {
        return originalXhrSend.apply(this, arguments);
      }
      const sanitized = sanitizeMessengerNetworkPayload(body, url, {
        method
      });
      const inspectBody = sanitized.changed ? sanitized.data : body;
      const blockType = shouldBlock(inspectBody, url, { method });
      traceNetwork("xhr", url, inspectBody, blockType);
      traceMessengerObservation("xhr", url, inspectBody, blockType);
      if (blockType) {
        return sendSyntheticJson(
          this,
          createBlockedPayload(blockType, url, inspectBody)
        );
      }
      if (sanitized.changed)
        return originalXhrSend.call(this, sanitized.data);
      return originalXhrSend.apply(this, arguments);
    };
    function sendSyntheticJson(xhr, payload) {
      const body = JSON.stringify(payload);
      const dataUrl = `data:application/json;charset=utf-8,${encodeURIComponent(body)}`;
      try {
        originalXhrOpen.call(
          xhr,
          "GET",
          dataUrl,
          xhr._ghostifyAsync !== false
        );
        return originalXhrSend.call(xhr);
      } catch (e) {
        return void 0;
      }
    }
    markGhostifyHook("xhr.hooked", { ok: true });
  }

  // src/platforms/facebook.js
  var REQUEST_NATIVE_GRACE_MS = 15e3;
  var ROOT_NATIVE_GRACE_MS = 15e3;
  var POPOVER_LOAD_NATIVE_GRACE_MS = 5e3;
  function startFacebookProtection() {
    if (!isFacebookDotCom) return;
    if (window.__GHOSTIFY_FACEBOOK_PROTECTION__) return;
    window.__GHOSTIFY_FACEBOOK_PROTECTION__ = true;
    if (isFacebookFeedRootRoute()) {
      activateRootNativeGrace(Date.now() + ROOT_NATIVE_GRACE_MS);
    }
    const markRequestIntent = (event) => {
      if (isFacebookMessageRequestNavigationTarget(event == null ? void 0 : event.target)) {
        const until = Date.now() + REQUEST_NATIVE_GRACE_MS;
        activateRequestNativeGrace(until);
      }
    };
    const markPopoverLoadIntent = (event) => {
      if (isFacebookMessengerPopoverButton(event == null ? void 0 : event.target)) {
        window.__GHOSTIFY_FACEBOOK_POPOVER_LOAD_UNTIL__ = Date.now() + POPOVER_LOAD_NATIVE_GRACE_MS;
        return;
      }
      window.__GHOSTIFY_FACEBOOK_POPOVER_LOAD_UNTIL__ = 0;
    };
    document.addEventListener("pointerdown", markRequestIntent, true);
    document.addEventListener("pointerdown", markPopoverLoadIntent, true);
    document.addEventListener("click", markRequestIntent, true);
    document.addEventListener("click", markPopoverLoadIntent, true);
    document.addEventListener(
      "keydown",
      (event) => {
        if ((event == null ? void 0 : event.key) !== "Enter" && (event == null ? void 0 : event.key) !== " ") return;
        markRequestIntent(event);
        markPopoverLoadIntent(event);
      },
      true
    );
  }
  function getFacebookSpoofState() {
    if (hasRecentMessageRequestIntent()) return null;
    if (isFacebookMessageRequestSurface()) return null;
    if (SETTINGS.msgSeen && !isKilled("msgSeen")) {
      if (hasRecentPopoverLoadIntent() && !isFacebookMessengerPopoverHydrated())
        return null;
      if (isFacebookFeedMessengerSurface()) return "unfocused-passive";
      if (isFacebookFeedRootSurface())
        return hasRootNativeGrace() ? null : "unfocused-passive";
      if (!isFacebookMessagingSurface()) return null;
      return "unfocused";
    }
    return null;
  }
  function activateRequestNativeGrace(until) {
    window.__GHOSTIFY_MESSAGE_REQUEST_FOCUS_UNTIL__ = until;
    window.__GHOSTIFY_MESSAGE_REQUEST_NATIVE_UNTIL__ = until;
    emitNativeFocusSignals();
  }
  function activateRootNativeGrace(until) {
    window.__GHOSTIFY_FACEBOOK_ROOT_NATIVE_UNTIL__ = Math.max(
      Number(window.__GHOSTIFY_FACEBOOK_ROOT_NATIVE_UNTIL__ || 0),
      until
    );
    emitNativeFocusSignals();
  }
  function hasRecentMessageRequestIntent() {
    return Math.max(
      Number(window.__GHOSTIFY_MESSAGE_REQUEST_FOCUS_UNTIL__ || 0),
      Number(window.__GHOSTIFY_MESSAGE_REQUEST_NATIVE_UNTIL__ || 0)
    ) > Date.now();
  }
  function hasRootNativeGrace() {
    return isFacebookFeedRootRoute() && Number(window.__GHOSTIFY_FACEBOOK_ROOT_NATIVE_UNTIL__ || 0) > Date.now();
  }
  function hasRecentPopoverLoadIntent() {
    return Number(window.__GHOSTIFY_FACEBOOK_POPOVER_LOAD_UNTIL__ || 0) > Date.now();
  }
  function emitNativeFocusSignals() {
    dispatchEventSafe(window, "focus");
    dispatchEventSafe(document, "visibilitychange");
    dispatchEventSafe(document, "webkitvisibilitychange");
    dispatchEventSafe(document, "focusin");
  }
  function dispatchEventSafe(target, type) {
    try {
      if (!target || typeof target.dispatchEvent !== "function") return;
      const event = typeof Event === "function" ? new Event(type, {
        bubbles: type === "focusin",
        cancelable: false
      }) : { type, target };
      target.dispatchEvent(event);
    } catch (e) {
    }
  }
  function isFacebookMessageRequestNavigationTarget(target) {
    const element = getClosestRequestElement(target);
    if (!element) return false;
    let current = element;
    for (let depth = 0; current && depth < 5; depth += 1) {
      const href = getElementAttribute(current, "href").toLowerCase();
      const label = [
        getElementAttribute(current, "aria-label"),
        getElementAttribute(current, "title"),
        current.innerText,
        current.textContent,
        href
      ].filter(Boolean).join(" ").toLowerCase();
      if (isFacebookMessageRequestRouteText(`${href} ${label}`)) return true;
      if (isFacebookConversationRouteText(href)) return false;
      current = current.parentElement;
    }
    return false;
  }
  function isFacebookMessengerPopoverButton(target) {
    const element = getClosestRequestElement(target);
    if (!element) return false;
    const label = [
      getElementAttribute(element, "aria-label"),
      getElementAttribute(element, "title"),
      element.innerText,
      element.textContent
    ].filter(Boolean).join(" ").trim().toLowerCase();
    return /^messenger(?:\s|,|$)/.test(label) && !getElementAttribute(element, "href").includes("/messages/");
  }
  function getClosestRequestElement(target) {
    if (!target || typeof target !== "object") return null;
    if (typeof target.closest === "function") {
      return target.closest(
        'a,button,[role="link"],[role="button"],[aria-label]'
      ) || target;
    }
    return target;
  }
  function getElementAttribute(element, name) {
    var _a;
    try {
      return String(((_a = element == null ? void 0 : element.getAttribute) == null ? void 0 : _a.call(element, name)) || "");
    } catch (e) {
      return "";
    }
  }
  function isFacebookMessagingSurface() {
    var _a, _b, _c;
    const path = String(((_a = window.location) == null ? void 0 : _a.pathname) || "").toLowerCase();
    const search = String(((_b = window.location) == null ? void 0 : _b.search) || "").toLowerCase();
    const hash = String(((_c = window.location) == null ? void 0 : _c.hash) || "").toLowerCase();
    if (path.startsWith("/messages") || path.startsWith("/messenger"))
      return true;
    if (search.includes("sk=messages") || hash.includes("messages"))
      return true;
    if (isFacebookFeedMessengerSurface()) return true;
    return false;
  }
  function isFacebookFeedRootSurface() {
    if (!isFacebookFeedRootRoute()) return false;
    if (isFacebookFeedMessengerSurface()) return false;
    return true;
  }
  function isFacebookFeedRootRoute() {
    var _a, _b, _c;
    const path = String(((_a = window.location) == null ? void 0 : _a.pathname) || "").toLowerCase();
    const search = String(((_b = window.location) == null ? void 0 : _b.search) || "").toLowerCase();
    const hash = String(((_c = window.location) == null ? void 0 : _c.hash) || "").toLowerCase();
    if (path !== "/" && path !== "/home.php") return false;
    if (search.includes("sk=messages") || hash.includes("messages"))
      return false;
    return true;
  }
  function isFacebookFeedMessengerSurface() {
    const hasMessengerPopover = hasDomElement('[role="dialog"][aria-label="Messenger"]') && hasDomElement('[role="grid"][aria-label="Chats"]');
    if (hasMessengerPopover) return true;
    if (hasDomElement('[aria-label^="Messages in conversation"]')) return true;
    const hasMiniChatChrome = hasDomElement('[aria-label="Minimize chat"]') || hasDomElement('[aria-label="Close chat"]');
    if (!hasMiniChatChrome) return false;
    return hasDomElement('[role="textbox"][contenteditable="true"]') || hasDomElement('[aria-label^="Write to"]') || hasDomElement('[aria-label^="Messages in conversation"]') || hasDomElement('[aria-label^="Conversation titled"]');
  }
  function isFacebookMessengerPopoverHydrated() {
    return hasDomElement('[role="dialog"][aria-label="Messenger"]') && hasDomElement('[role="grid"][aria-label="Chats"]');
  }
  function hasDomElement(selector) {
    return !!getDomElement(selector);
  }
  function getDomElement(selector) {
    try {
      return typeof (document == null ? void 0 : document.querySelector) === "function" ? document.querySelector(selector) : null;
    } catch (e) {
      return null;
    }
  }
  function isFacebookMessageRequestSurface() {
    var _a, _b, _c;
    const path = String(((_a = window.location) == null ? void 0 : _a.pathname) || "").toLowerCase();
    const search = String(((_b = window.location) == null ? void 0 : _b.search) || "").toLowerCase();
    const hash = String(((_c = window.location) == null ? void 0 : _c.hash) || "").toLowerCase();
    const route = `${path} ${search} ${hash}`;
    return isFacebookMessageRequestRouteText(route);
  }
  function isFacebookMessageRequestRouteText(routeText) {
    const route = String(routeText || "").toLowerCase();
    return route.includes("/messages/requests") || route.includes("/messages/message-requests") || route.includes("/messages/message_requests") || route.includes("folder=message_requests") || route.includes("folder=pending_threads") || route.includes("folder=filtered_threads") || route.includes("folder=spam_threads") || route.includes("message requests") || route.includes("message-requests") || route.includes("message_requests") || route.includes("pending_threads") || route.includes("filtered_threads") || route.includes("spam_threads");
  }
  function isFacebookConversationRouteText(routeText) {
    const route = String(routeText || "").toLowerCase();
    return route.includes("/messages/t/") || route.includes("/messages/e2ee/t/");
  }

  // src/platforms/instagram.js
  function startInstagramProtection() {
    if (!isInstagram || window.__GHOSTIFY_INSTAGRAM_PROTECTION__) return;
    window.__GHOSTIFY_INSTAGRAM_PROTECTION__ = true;
  }
  function getInstagramSpoofState() {
    const seenEnabled = SETTINGS.igSeen && !isKilled("igSeen");
    const storyEnabled = SETTINGS.igStory && !isKilled("igStory");
    if (!seenEnabled && !storyEnabled) return null;
    if (storyEnabled && isStorySurface()) {
      return "unfocused";
    }
    if (isMediaPlaybackSurface()) return null;
    if (seenEnabled && !isDirectSurface()) {
      return "unfocused";
    }
    return null;
  }
  function isStorySurface() {
    return window.location.pathname.startsWith("/stories/");
  }
  function isDirectSurface() {
    const path = window.location.pathname;
    return path === "/direct/" || path.startsWith("/direct/");
  }
  function isMediaPlaybackSurface() {
    var _a;
    const path = String(((_a = window.location) == null ? void 0 : _a.pathname) || "").toLowerCase();
    return path === "/" || path === "/reel" || path.startsWith("/reel/") || path === "/reels" || path.startsWith("/reels/") || path === "/p" || path.startsWith("/p/") || path === "/tv" || path.startsWith("/tv/") || path === "/explore" || path.startsWith("/explore/");
  }

  // src/platforms/messenger.js
  var REQUEST_NATIVE_GRACE_MS2 = 15e3;
  function startMessengerProtection() {
    if (!isMessengerDotCom && !isFacebookMessengerProxy) return;
    if (window.__GHOSTIFY_MESSENGER_PROTECTION__) return;
    window.__GHOSTIFY_MESSENGER_PROTECTION__ = true;
    const markRequestIntent = (event) => {
      if (isMessageRequestNavigationTarget(event == null ? void 0 : event.target)) {
        const until = Date.now() + REQUEST_NATIVE_GRACE_MS2;
        activateRequestNativeGrace2(until);
      }
    };
    document.addEventListener("pointerdown", markRequestIntent, true);
    document.addEventListener("click", markRequestIntent, true);
    document.addEventListener(
      "keydown",
      (event) => {
        if ((event == null ? void 0 : event.key) !== "Enter" && (event == null ? void 0 : event.key) !== " ") return;
        markRequestIntent(event);
      },
      true
    );
  }
  function getMessengerSpoofState() {
    if (hasRecentMessageRequestIntent2()) return null;
    if (isMessengerMessageRequestSurface()) return null;
    if (SETTINGS.msgSeen && !isKilled("msgSeen")) {
      if (isFacebookMessengerProxy) return "unfocused-passive";
      return "unfocused";
    }
    return null;
  }
  function activateRequestNativeGrace2(until) {
    window.__GHOSTIFY_MESSAGE_REQUEST_FOCUS_UNTIL__ = until;
    window.__GHOSTIFY_MESSAGE_REQUEST_NATIVE_UNTIL__ = until;
    emitNativeFocusSignals2();
  }
  function hasRecentMessageRequestIntent2() {
    return Math.max(
      Number(window.__GHOSTIFY_MESSAGE_REQUEST_FOCUS_UNTIL__ || 0),
      Number(window.__GHOSTIFY_MESSAGE_REQUEST_NATIVE_UNTIL__ || 0)
    ) > Date.now();
  }
  function emitNativeFocusSignals2() {
    dispatchEventSafe2(window, "focus");
    dispatchEventSafe2(document, "visibilitychange");
    dispatchEventSafe2(document, "webkitvisibilitychange");
    dispatchEventSafe2(document, "focusin");
  }
  function dispatchEventSafe2(target, type) {
    try {
      if (!target || typeof target.dispatchEvent !== "function") return;
      const event = typeof Event === "function" ? new Event(type, {
        bubbles: type === "focusin",
        cancelable: false
      }) : { type, target };
      target.dispatchEvent(event);
    } catch (e) {
    }
  }
  function isMessageRequestNavigationTarget(target) {
    const element = getClosestRequestElement2(target);
    if (!element) return false;
    const href = getElementAttribute2(element, "href");
    const label = [
      getElementAttribute2(element, "aria-label"),
      getElementAttribute2(element, "title"),
      element.innerText,
      element.textContent,
      href
    ].filter(Boolean).join(" ").toLowerCase();
    return href.includes("/requests") || href.includes("message_requests") || href.includes("message-requests") || label.includes("message requests") || label.includes("message_requests") || label.includes("message-requests") || /^requests(?:\s|[^\w\s]|$)/.test(label.trim());
  }
  function getClosestRequestElement2(target) {
    if (!target || typeof target !== "object") return null;
    if (typeof target.closest === "function") {
      return target.closest(
        'a,button,[role="link"],[role="button"],[aria-label]'
      ) || target;
    }
    return target;
  }
  function getElementAttribute2(element, name) {
    var _a;
    try {
      return String(((_a = element == null ? void 0 : element.getAttribute) == null ? void 0 : _a.call(element, name)) || "");
    } catch (e) {
      return "";
    }
  }
  function isMessengerMessageRequestSurface() {
    var _a, _b, _c;
    const path = String(((_a = window.location) == null ? void 0 : _a.pathname) || "").toLowerCase();
    const search = String(((_b = window.location) == null ? void 0 : _b.search) || "").toLowerCase();
    const hash = String(((_c = window.location) == null ? void 0 : _c.hash) || "").toLowerCase();
    const route = `${path} ${search} ${hash}`;
    return path.startsWith("/requests") || path.startsWith("/message-requests") || path.startsWith("/message_requests") || route.includes("folder=message_requests") || route.includes("message_requests") || route.includes("message-requests");
  }

  // src/core/interceptors/focus.js
  var FOCUS_EVENTS = [
    "visibilitychange",
    "webkitvisibilitychange",
    "blur",
    "focus",
    "focusin",
    "focusout"
  ];
  function shouldSpoofVisibility() {
    if (isMessengerDotCom || isFacebookMessengerProxy) {
      const state = getMessengerSpoofState();
      if (state !== null) return state;
    }
    if (isFacebookDotCom) {
      const state = getFacebookSpoofState();
      if (state !== null) return state;
    }
    if (isInstagram) {
      const state = getInstagramSpoofState();
      if (state !== null) return state;
    }
    return false;
  }
  function hookVisibility() {
    if (window.__GHOSTIFY_VISIBILITY_HOOKED__) return;
    window.__GHOSTIFY_VISIBILITY_HOOKED__ = true;
    const originalHasFocus = document.hasFocus.bind(document);
    const originalVisibilityState = getPropertyDescriptor("visibilityState");
    const originalHidden = getPropertyDescriptor("hidden");
    const originalAddEventListener = EventTarget.prototype.addEventListener;
    const originalRemoveEventListener = EventTarget.prototype.removeEventListener;
    const wrappedListeners = /* @__PURE__ */ new WeakMap();
    Object.defineProperty(document, "hasFocus", {
      value: function() {
        const spoof = shouldSpoofVisibility();
        if (spoof === "hidden" || spoof === "unfocused" || spoof === "unfocused-passive")
          return false;
        return originalHasFocus();
      },
      configurable: true
    });
    Object.defineProperty(document, "visibilityState", {
      get: function() {
        const spoof = shouldSpoofVisibility();
        if (spoof === "hidden") return "hidden";
        return (originalVisibilityState == null ? void 0 : originalVisibilityState.get) ? originalVisibilityState.get.call(document) : "visible";
      },
      configurable: true
    });
    Object.defineProperty(document, "hidden", {
      get: function() {
        const spoof = shouldSpoofVisibility();
        if (spoof === "hidden") return true;
        return (originalHidden == null ? void 0 : originalHidden.get) ? originalHidden.get.call(document) : false;
      },
      configurable: true
    });
    EventTarget.prototype.addEventListener = function(type, listener, options) {
      if (!FOCUS_EVENTS.includes(type) || !listener) {
        return originalAddEventListener.call(this, type, listener, options);
      }
      const wrapped = getWrappedListener(type, listener, wrappedListeners);
      return originalAddEventListener.call(this, type, wrapped, options);
    };
    EventTarget.prototype.removeEventListener = function(type, listener, options) {
      const wrapped = FOCUS_EVENTS.includes(type) ? findWrappedListener(type, listener, wrappedListeners) : null;
      return originalRemoveEventListener.call(
        this,
        type,
        wrapped || listener,
        options
      );
    };
  }
  function getPropertyDescriptor(prop) {
    let proto = Document.prototype;
    while (proto) {
      const descriptor = Object.getOwnPropertyDescriptor(proto, prop);
      if (descriptor) return descriptor;
      proto = Object.getPrototypeOf(proto);
    }
    return null;
  }
  function getWrappedListener(type, listener, wrappedListeners) {
    let typeMap = wrappedListeners.get(listener);
    if (!typeMap) {
      typeMap = /* @__PURE__ */ new Map();
      wrappedListeners.set(listener, typeMap);
    }
    if (typeMap.has(type)) return typeMap.get(type);
    const wrapped = function(event) {
      const spoof = shouldSpoofVisibility();
      if (spoof) {
        const suppressFocusEvents = spoof === "hidden" || spoof === "unfocused";
        if (suppressFocusEvents && (type === "blur" || type === "focus" || type === "focusin" || type === "focusout")) {
          if (this === window || this === document || event && (event.target === window || event.target === document)) {
            return;
          }
        } else if (spoof === "hidden") {
          return;
        }
      }
      if (typeof listener === "function") {
        return listener.call(this, event);
      }
      if (listener && typeof listener.handleEvent === "function") {
        return listener.handleEvent.call(listener, event);
      }
    };
    typeMap.set(type, wrapped);
    return wrapped;
  }
  function findWrappedListener(type, listener, wrappedListeners) {
    var _a;
    if (!listener) return null;
    return ((_a = wrappedListeners.get(listener)) == null ? void 0 : _a.get(type)) || null;
  }

  // src/ghost.js
  (function() {
    "use strict";
    if (!(isInstagram || isMessengerDotCom || isFacebookDotCom || isFacebookMessengerProxy))
      return;
    if (window.__GHOSTIFY_GHOST_HOOKED__) return;
    window.__GHOSTIFY_GHOST_HOOKED__ = true;
    traceMessengerHealth("ghost.init", {
      world: "MAIN",
      readyState: document.readyState
    });
    if (isFacebookDotCom && !isMessengerDotCom && window.top !== window) {
      traceMessengerHealth("facebook.child_frame_reduced", {
        reason: "network_hooks_only"
      });
    }
    window.postMessage(
      {
        type: "GHOSTIFY_SETTINGS_REQUEST",
        source: "GHOSTIFY_PAGE"
      },
      window.location.origin
    );
    window.addEventListener("message", (event) => {
      if (event.source !== window) return;
      if (!event.data || event.data.source !== "GHOSTIFY_EXTENSION") return;
      if (event.data.type === "GHOSTIFY_CONFIG_UPDATE") {
        const CONFIG = event.data.config;
        updatePatterns(CONFIG == null ? void 0 : CONFIG.patterns);
        updateKillSwitch(CONFIG == null ? void 0 : CONFIG.killSwitch);
      }
      if (event.data.type === "GHOSTIFY_SETTINGS_UPDATE") {
        const settings = normalizeSettings(event.data.settings);
        if (settings) {
          Object.assign(SETTINGS, settings);
          markSettingsReady();
          traceMessengerHealth("settings.update", {
            msgSeen: SETTINGS.msgSeen,
            msgTyping: SETTINGS.msgTyping
          });
        }
      }
    });
    hookWebSocket();
    if (isInstagram || isMessengerDotCom || isFacebookDotCom || isFacebookMessengerProxy) {
      hookVisibility();
    }
    hookFetch();
    hookXHR();
    startFacebookProtection();
    startMessengerProtection();
    startInstagramProtection();
  })();
  function normalizeSettings(settings) {
    if (!settings || typeof settings !== "object") return null;
    const normalized = {};
    for (const key of Object.keys(SETTINGS)) {
      if (typeof settings[key] === "boolean") {
        normalized[key] = settings[key];
      }
    }
    return Object.keys(normalized).length ? normalized : null;
  }
  function updateKillSwitch(killSwitch) {
    KILLED_FEATURES.clear();
    if (!Array.isArray(killSwitch)) return;
    for (const feature of killSwitch) {
      if (typeof feature === "string" && Object.prototype.hasOwnProperty.call(SETTINGS, feature)) {
        KILLED_FEATURES.add(feature);
      }
    }
  }
})();
