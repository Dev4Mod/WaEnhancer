import $ from "https://v2.blissfuljs.com/src/$.js";
import $$ from "https://v2.blissfuljs.com/src/$$.js";
import create from "https://v2.blissfuljs.com/src/dom/create.js";
import bind from "https://v2.blissfuljs.com/src/events/bind.js";
import load from "https://v2.blissfuljs.com/src/async/load.js";

Object.assign($, {create, bind, load});
export { $, $$, create, bind, load};

/**
 * Utility for regexp construction
 * @param {*} s
 * @returns
 */
let escape = s => s.replace(/[-\/\\^$*+?.()|[\]{}]/g, "\\$&");
let _regexp = (flags, strings, ...values) => {
	let pattern = strings[0] + values.map((v, i) => escape(v) + strings[i + 1]).join("");
	return RegExp(pattern, flags);
};
let cache = {};

export const regexp = new Proxy(_regexp.bind(this, ""), {
	get: (t, property) => {
		return t[property] || cache[property]
				|| (cache[property] = _regexp.bind(this, property));
	}
});

export function loadLanguages (ids, PrismLive) {
	ids = Array.isArray(ids) ? ids : ids.split(/,/);
	return ids.map(c => import(`./languages/${c}.mjs`).then(m => {
		if (m.default) {
			PrismLive.registerLanguage(m.default.id, m.default);
		}
		else {
			// Many languages
			for (let id in m) {
				if (PrismLive.languages[id]) {
					// Already registered, augment it
					Object.assign(PrismLive.languages[id], m[id]);
				}
				else {
					PrismLive.registerLanguage(id, m[id]);
				}

			}
		}
	}));
}