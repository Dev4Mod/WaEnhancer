
{
	let url, importURL = "./prism-live.mjs";
	// Fall back to loading all languages
	let search = "?load=css,javascript,markup";

	try {
		url = document.currentScript?.src ?? eval("import.meta.url");
	}
	catch(e) {}

	if (url) {
		importURL = new URL(importURL, url);
		importURL.search = new URL(url).search ?? search;
	}

	import(importURL).then(m => Prism.Live = m.default);
}
