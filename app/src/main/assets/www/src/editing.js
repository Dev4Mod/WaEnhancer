import * as env from "./env.js";
import * as defaults from "./defaults.js";
import {regexp} from "./util.js";

export function checkShortcut (shortcut, evt) {
	return shortcut.trim().split(/\s*\+\s*/).every(key => {
		switch (key) {
			case "Cmd":   return evt[env.superKey];
			case "Ctrl":  return evt.ctrlKey;
			case "Shift": return evt.shiftKey;
			case "Alt":   return evt.altKey;
			default:      return evt.key === key;
		}
	});
}

const LF = "\n";
const CR = "\r";

export function getLineBounds (context = this) {
	var value = context.value;
	var start, end, char;

	for (var start = context.selectionStart; char = value[start]; start--) {
		if (char === LF || char === CR || !start) {
			break;
		}
	}

	for (var end = context.selectionStart; char = value[end]; end++) {
		if (char === LF || char === CR) {
			break;
		}
	}

	return {start, end};
}

export function beforeCaretIndex (until = "", context = this) {
	return context.value.lastIndexOf(until, context.selectionStart);
}

export function afterCaretIndex (until = "", context = this) {
	return context.value.indexOf(until, context.selectionEnd);
}

export function beforeCaret (until = "", context = this) {
	var index = beforeCaretIndex(until, context);

	if (index === -1 || !until) {
		index = 0;
	}

	return context.value.slice(index, context.selectionStart);
};

export function afterCaret (until = "", context = this) {
	var index = afterCaretIndex(until);

	if (index === -1 || !until) {
		index = undefined;
	}

	return this.value.slice(context.selectionEnd, index);
}

export function setCaret (pos, context = this) {
	context.selectionStart = context.selectionEnd = pos;
}

export function moveCaret (chars, context = this) {
	if (chars) {
		context.setCaret(context.selectionEnd + chars);
	}
}

export function deleteText (characters, {selectionStart, selectionEnd, forward, pos} = {}) {
	var i = characters = characters > 0? characters : (characters + "").length;
	let ret = { selectionStart, selectionEnd };

	if (pos) {
		ret.selectionStart = pos;
		ret.selectionEnd = pos + ret.selectionEnd - selectionStart;
	}

	while (i--) {
		document.execCommand(forward? "forwardDelete" : "delete");
	}

	if (pos) {
		// Restore caret
		ret.selectionStart = selectionStart - characters;
		ret.selectionEnd = ret.selectionEnd - pos + ret.selectionStart;
	}

	return ret;
}

export function matchIndentation (text, currentIndent) {
	// FIXME this assumes that text has no indentation of its own
	// to make this more generally useful beyond snippets, we should first
	// strip text's own indentation.
	text = text.replace(/\r?\n/g, "$&" + currentIndent);
}

export function adjustIndentation (text, {indentation, relative = true, indent = defaults.indent}) {
	if (!relative) {
		// First strip min indentation
		let minIndent = text.match(regexp.gm`^(${indent})+`).sort()[0];

		if (minIndent) {
			text.replace(regexp.gm`^${minIndent}`, "");
		}
	}

	if (indentation < 0) {
		return text.replace(regexp.gm`^${indent}`, "");
	}
	else if (indentation > 0) { // Indent
		return text.replace(/^/gm, indent);
	}
}