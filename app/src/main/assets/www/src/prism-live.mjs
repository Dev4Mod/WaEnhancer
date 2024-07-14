/**
	Prism Live: Code editor based on Prism.js
	Works best in Chrome. Currently only very basic support in other browsers (no snippets, no shortcuts)
	@author Lea Verou
*/
import { $, $$, regexp, loadLanguages } from "./util.js";
import {
	checkShortcut,
	getLineBounds,
	beforeCaretIndex,
	beforeCaret,
	afterCaretIndex,
	afterCaret,
	setCaret,
	moveCaret,
	deleteText,
	matchIndentation,
	adjustIndentation,
} from "./editing.js";
import {
	getNode,
	getOffset,
} from "./dom.js";
import * as env from "./env.js";
import * as defaults from "./defaults.js";

export default class PrismLive {
	constructor(source) {
		this.source = source;
		this.sourceType = source.nodeName.toLowerCase();

		this.wrapper = $.create({
			className: "prism-live",
			around: this.source
		});

		if (this.sourceType === "textarea") {
			this.textarea = this.source;
			this.code = document.createElement("code");

			this.pre = $.create("pre", {
				className: this.textarea.className + " no-whitespace-normalization",
				contents: this.code,
				before: this.textarea
			});
		}
		else {
			this.pre = this.source;
			// Normalize once, to fix indentation from markup and then remove normalization
			// so we can enter blank lines etc

			// Prism.plugins.NormalizeWhitespace.normalize($("code", this.pre), {});
			this.pre.classList.add("no-whitespace-normalization");
			this.code = $("code", this.pre);

			this.textarea = $.create("textarea", {
				className: this.pre.className,
				value: this.pre.textContent,
				after: this.pre
			});
		}

		self.all.set(this.textarea, this);
		self.all.set(this.pre, this);
		self.all.set(this.code, this);

		this.pre.classList.add("prism-live");
		this.textarea.classList.add("prism-live");
		this.source.classList.add("prism-live-source");

		if (self.Incrementable) {
			// TODO data-* attribute for modifier
			// TODO load dynamically if not present
			new Incrementable(this.textarea);
		}

		$.bind(this.textarea, {
			input: evt => this.update(),

			keyup: evt => {
				if (evt.key == "Enter") { // Enter
					// Maintain indent on line breaks
					this.insert(this.currentIndent);
					this.syncScroll();
				}
			},

			keydown: evt => {
				if (evt.key == "Tab" && !evt.altKey) {
					// Default is to move focus off the textarea
					// this is never desirable in an editor
					evt.preventDefault();

					if (this.tabstops && this.tabstops.length > 0) {
						// We have tabstops to go
						this.moveCaret(this.tabstops.shift());
					}
					else if (this.hasSelection) {
						var before = this.beforeCaret("\n");
						var outdent = evt.shiftKey;

						this.selectionStart -= before.length;

						var selection = adjustIndentation(this.selection, {
							relative: true,
							indentation: outdent? -1 : 1
						});

						this.replace(selection);

						if (outdent) {
							var indentStart = regexp.gm`^${this.indent}`;
							var isBeforeIndented = indentStart.test(before);
							this.selectionStart += before.length + 1 - (outdent + isBeforeIndented);
						}
						else { // Indent
							var hasLineAbove = before.length == this.selectionStart;
							this.selectionStart += before.length + 1 + !hasLineAbove;
						}
					}
					else {
						// Nothing selected, expand snippet
						let selector = this.beforeCaret()?.match(/\S*$/)?.[0];
						var snippetExpanded = this.expandSnippet(selector);

						if (snippetExpanded) {
							requestAnimationFrame(() => this.textarea.dispatchEvent(new InputEvent("input", {bubbles: true})));
						}
						else {
							this.insert(this.indent);
						}
					}
				}
				else if (self.pairs[evt.key] && !evt[env.superKey]) {
					var other = self.pairs[evt.key];
					this.wrapSelection({
						before: evt.key,
						after: other,
						outside: true
					});
					evt.preventDefault();
				}
				else if (Object.values(self.pairs).includes(evt.key)) {
					if (this.selectionStart == this.selectionEnd && this.textarea.value[this.selectionEnd] == evt.key) {
						this.selectionStart += 1;
						this.selectionEnd += 1;
						evt.preventDefault();
					}
				}
				else {
					for (let shortcut in self.shortcuts) {
						if (checkShortcut(shortcut, evt)) {
							self.shortcuts[shortcut].call(this, evt);
							evt.preventDefault();
						}
					}
				}
			},

			click: evt => {
				var l = this.getLine();
				var v = this.value;
				var ss = this.selectionStart;
				//console.log(ss, v[ss], l, v.slice(l.start, l.end));
			},

			"click keyup": evt => {
				if (!evt.key || evt.key.lastIndexOf("Arrow") > -1) {
					// Caret moved
					this.tabstops = null;
				}
			}
		});

		// this.syncScroll();
		this.textarea.addEventListener("scroll", this, {passive: true});

		$.bind(window, {
			"resize": evt => this.syncStyles()
		});

		// Copy styles with a delay
		requestAnimationFrame(() => {
			this.syncStyles();

			var sourceCS = getComputedStyle(this.source);

			this.pre.style.height = this.source.style.height || sourceCS.getPropertyValue("--height");
			this.pre.style.maxHeight = this.source.style.maxHeight || sourceCS.getPropertyValue("--max-height");
			this.textarea.spellcheck = this.source.spellcheck || sourceCS.getPropertyValue("--spellcheck");
		});

		this.update();
		this.lang = (this.code.className.match(/lang(?:uage)?-(\w+)/i) || [,])[1];

		this.observer = new MutationObserver(r => {
			if (document.activeElement !== this.textarea) {
				this.textarea.value = this.pre.textContent;
			}
		});

		this.observe();

		this.source.dispatchEvent(new CustomEvent("prism-live-init", {bubbles: true, detail: this}));
	}

	handleEvent(evt) {
		if (evt.type === "scroll") {
			this.syncScroll();
		}
	}

	observe () {
		return this.observer && this.observer.observe(this.pre, {
			childList: true,
			subtree: true,
			characterData: true
		});
	}

	unobserve () {
		if (this.observer) {
			this.observer.takeRecords();
			this.observer.disconnect();
		}
	}

	expandSnippet(text) {
		if (!text) {
			return false;
		}

		var context = this.context;

		if (text in context.snippets || text in self.snippets) {
			// Static Snippets
			var expansion = context.snippets[text] || self.snippets[text];
		}
		else if (context.snippets.custom) {
			var expansion = context.snippets.custom.call(this, text);
		}

		if (expansion) {
			// Insert snippet
			var stops = [];
			var replacement = [];
			var str = expansion;
			var match;

			while (match = self.CARET_INDICATOR.exec(str)) {
				stops.push(match.index + 1);
				replacement.push(str.slice(0, match.index + match[1].length));
				str = str.slice(match.index + match[0].length);
				self.CARET_INDICATOR.lastIndex = 0;
			}

			replacement.push(str);
			replacement = replacement.join("");

			if (stops.length > 0) {
				// make first stop relative to end, all others relative to previous stop
				stops[0] -= replacement.length;
			}

			this.delete(text);
			this.insert(replacement, {matchIndentation: true});
			this.tabstops = stops;
			this.moveCaret(this.tabstops.shift());
		}

		return !!expansion;
	}

	get selectionStart() {
		return this.textarea.selectionStart;
	}
	set selectionStart(v) {
		this.textarea.selectionStart = v;
	}

	get selectionEnd() {
		return this.textarea.selectionEnd;
	}
	set selectionEnd(v) {
		this.textarea.selectionEnd = v;
	}

	get hasSelection() {
		return this.selectionStart != this.selectionEnd;
	}

	get selection() {
		return this.value.slice(this.selectionStart, this.selectionEnd);
	}

	get value() {
		return this.textarea.value;
	}
	set value(v) {
		this.textarea.value = v;
	}

	get indent() {
		return this.value.match(/^[\t ]+/m)?.[0] ?? defaults.indent;
	}

	get currentIndent() {
		let before = this.value.slice(0, this.selectionStart-1);
		return before.match(/^[\t ]*/mg)?.at(-1) ?? "";
	}

	// Current language at caret position
	get currentLanguage() {
		var node = this.getNode();
		node = node? node.parentNode : this.code;
		let lang = node.closest('[class*="language-"]')?.className.match(/language-(\w+)/)?.[1];
		return self.aliases[lang] || lang;
	}

	// Get settings based on current language
	get context() {
		var lang = this.currentLanguage;
		return self.languages[lang] || self.languages.DEFAULT;
	}

	setSelection(start, end) {
		if (start && typeof start === "object" && (start.start || start.end)) {
			end = start.end;
			start = start.start;
		}

		let prevStart = this.selectionStart;
		let prevEnd = this.selectionEnd;

		if (start !== undefined) {
			this.selectionStart = start;
		}

		if (end !== undefined) {
			this.selectionEnd = end;
		}

		// If there is a selection, and it's not the same as the previous selection, fire appropriate select event
		if (this.selectionStart !== this.selectionEnd && (prevStart !== this.selectionStart || prevEnd !== this.selectionEnd)) {
			this.textarea.dispatchEvent(new Event("select", {bubbles: true}));
		}
	}

	update (force) {
		var code = this.value;

		// If code ends in newline then browser "conveniently" trims it
		// but we want to see the new line we just inserted!
		// So we insert a zero-width space, which isn't trimmed
		if (/\n$/.test(this.value)) {
			code += "\u200b";
		}

		if (!force && this.code.textContent === code && $(".token", this.code)) {
			// Already highlighted
			return;
		}

		this.unobserve();
		this.code.textContent = code;

		Prism.highlightElement(this.code);

		this.observe();
	}

	syncStyles() {
		// Copy pre metrics over to textarea
		var cs = getComputedStyle(this.pre);

		// Copy styles from <pre> to textarea
		this.textarea.style.caretColor = cs.color;

		var properties = /^(font|lineHeight)|[tT]abSize/gi;

		for (var prop in cs) {
			if (cs[prop] && prop in this.textarea.style && properties.test(prop)) {
				this.wrapper.style[prop] = cs[prop];
				this.textarea.style[prop] = this.pre.style[prop] = "inherit";
			}
		}

		// This is primarily for supporting the line-numbers plugin.
		this.textarea.style['padding-left'] = cs['padding-left'];

		this.update();
	}

	syncScroll() {
		if (this.pre.clientWidth === 0 && this.pre.clientHeight === 0) {
			return;
		}

		this.pre.scrollTop = this.textarea.scrollTop;
		this.pre.scrollLeft = this.textarea.scrollLeft;
	}

	beforeCaretIndex (until = "") {
		return beforeCaretIndex(until, this);
	}

	afterCaretIndex (until = "") {
		return afterCaretIndex(until, this);
	}

	beforeCaret (until = "") {
		return beforeCaret(until, this);
	}

	getLine () {
		return getLineBounds(this);
	}

	afterCaret (until = "") {
		return afterCaret(until, this);
	}

	setCaret (pos) {
		return setCaret(pos, this);
	}

	moveCaret (chars) {
		return moveCaret(chars, this);
	}

	insert (text, {index} = {}) {
		if (!text) {
			return;
		}

		this.textarea.focus();

		if (index === undefined) {
			// No specified index, insert in current caret position
			this.replace(text);
		}
		else {
			// Specified index, first move caret there
			var start = this.selectionStart;
			var end = this.selectionEnd;

			this.selectionStart = this.selectionEnd = index;
			this.replace(text);

			this.setSelection(
				start + (index < start? text.length : 0),
				end + (index <= end? text.length : 0)
			);
		}
	}

	// Replace currently selected text
	replace (text) {
		var hadSelection = this.hasSelection;

		this.insertText(text);

		if (hadSelection) {
			// By default inserText places the caret at the end, losing any selection
			// What we want instead is the replaced text to be selected
			this.setSelection({start: this.selectionEnd - text.length});
		}
	}

	// Set text between indexes and restore caret position
	set (text, {start, end} = {}) {
		var ss = this.selectionStart;
		var se = this.selectionEnd;

		this.setSelection(start, end);

		this.insertText(text);

		this.setSelection(ss, se);
	}

	insertText (text) {
		if (!text) {
			return;
		}

		return document.execCommand("insertText", false, text);
	}

	/**
	 * Wrap text with strings
	 * @param before {String} The text to insert before
	 * @param after {String} The text to insert after
	 * @param start {Number} Character offset
	 * @param end {Number} Character offset
	 */
	wrap ({before, after, start = this.selectionStart, end = this.selectionEnd} = {}) {
		var ss = this.selectionStart;
		var se = this.selectionEnd;
		var between = this.value.slice(start, end);

		this.set(before + between + after, {start, end});

		if (ss > start) {
			ss += before.length;
		}

		if (se > start) {
			se += before.length;
		}

		if (ss > end) {
			ss += after.length;
		}

		if (se > end) {
			se += after.length;
		}

		this.setSelection(ss, se);
	}

	wrapSelection (o = {}) {
		var hadSelection = this.hasSelection;

		this.replace(o.before + this.selection + o.after);

		if (hadSelection) {
			if (o.outside) {
				// Do not include new text in selection
				this.selectionStart += o.before.length;
				this.selectionEnd -= o.after.length;
			}
		}
		else {
			this.moveCaret(-o.after.length);
		}
	}

	toggleComment() {
		var comments = this.context.comments;

		// Are we inside a comment?
		var node = this.getNode();
		var commentNode = node.parentNode.closest(".token.comment");

		if (commentNode) {
			// Remove comment
			var start = this.getOffset(commentNode);
			var commentText = commentNode.textContent;

			if (comments.singleline && commentText.indexOf(comments.singleline) === 0) {
				var end = start + commentText.length;
				this.set(this.value.slice(start + comments.singleline.length, end), {start, end});
				this.moveCaret(-comments.singleline.length);
			}
			else {
				comments = comments.multiline || comments;
				var end = start + commentText.length - comments[1].length;
				this.set(this.value.slice(start + comments[0].length, end), {start, end: end + comments[1].length});
			}
		}
		else {
			// Not inside comment, add
			if (this.hasSelection) {
				comments = comments.multiline || comments;

				this.wrapSelection({
					before: comments[0],
					after: comments[1]
				});
			}
			else {
				// No selection, wrap line
				// FIXME *inside indent*
				comments = comments.singleline? [comments.singleline, ""] : comments.multiline || comments;
				end = this.afterCaretIndex("\n");
				this.wrap({
					before: comments[0],
					after: comments[1],
					start: this.beforeCaretIndex("\n") + 1,
					end: end < 0? this.value.length : end
				});
			}
		}
	}

	duplicateContent () {
		var before = this.beforeCaret("\n");
		var after = this.afterCaret("\n");
		var text = before + this.selection + after;

		this.insert(text, {index: this.selectionStart - before.length});
	}

	delete (characters, {forward, pos} = {}) {
		let { selectionStart, selectionEnd } = deleteText(characters, {forward, pos, selectionStart: this.selectionStart, selectionEnd: this.selectionEnd});

		this.setSelection(selectionStart, selectionEnd);
	}

	/**
	 * Get the text node at a given chracter offset
	 */
	getNode (offset = this.selectionStart, container = this.code) {
		return getNode(offset, container);
	}

	/**
	 * Get the character offset of a given node in the highlighted source
	 */
	getOffset (node) {
		return getOffset(node, this.code);
	}

	static registerLanguage(name, context, parent = self.languages.DEFAULT) {
		Object.setPrototypeOf(context, parent);
		return self.languages[name] = context;
	}

	static create (source, ...args) {
		let ret = self.all.get(source);
		if (!ret) {
			ret = new self(source);
		}
		return ret;
	}
};

let self = PrismLive;
Prism.Live ??= PrismLive;

// Static properties
Object.assign(self, {
	all: new WeakMap(),
	DEFAULT_INDENT: defaults.indent,
	CARET_INDICATOR: /(^|[^\\])\$(\d+)/g,
	snippets: {
		"test": "Snippets work!",
	},
	pairs: {
		"(": ")",
		"[": "]",
		"{": "}",
		'"': '"',
		"'": "'",
		"`": "`"
	},
	shortcuts: {
		"Cmd + /": function() {
			this.toggleComment();
		},
		"Ctrl + Shift + D": function() {
			this.duplicateContent();
		}
	},
	languages: {
		DEFAULT: {
			comments: {
				multiline: ["/*", "*/"]
			},
			snippets: {}
		}
	},
	// Map of Prism language ids and their canonical name
	aliases: (() => {
		var ret = {};
		var canonical = new WeakMap(Object.entries(Prism.languages).map(x => x.reverse()).reverse());

		for (var id in Prism.languages) {
			var grammar = Prism.languages[id];

			if (typeof grammar !== "function") {
				ret[id] = canonical.get(grammar);
			}
		}

		return ret;
	})(),
});

export const dependencies = [
	$.load(new URL("../prism-live.css", import.meta.url)),
];

let url = new URL(import.meta.url);
let urlParams = url.searchParams;

if (urlParams.has("load")) {
	// Tiny dynamic loader. Use e.g. ?load=css,markup,javascript to load components
	let load = urlParams.get("load");
	if (load !== null) {
		let promises = loadLanguages(load, PrismLive);
		dependencies.push(...promises);
	}
}

export const ready = Promise.all(dependencies);
self.ready = ready;

$$(":not(.prism-live) > textarea.prism-live, :not(.prism-live) > pre.prism-live").forEach(source => self.create(source));


