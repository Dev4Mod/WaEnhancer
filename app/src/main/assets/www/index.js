/*
 * JS for Prism Live’s page, not part of the actual editor
 */

(async function($, $$) {

$$("textarea.language-html.fill").forEach(t => t.value = document.head.outerHTML);

var css = await fetch("prism-live.css");
css = await css.text();

$$("textarea.language-css.fill").forEach(t => {
	t.value = css;
	t.dispatchEvent(new InputEvent("input"));
});

var js = await fetch("src/prism-live.mjs");
js = await js.text();

$$("textarea.language-js.fill").forEach(t => {
	t.value = js;
	t.dispatchEvent(new InputEvent("input"));
});


})(Bliss, Bliss.$);
