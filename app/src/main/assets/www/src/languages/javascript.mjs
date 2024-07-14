export const clike = {
	id: "clike",
	comments: {
		singleline: "//",
		multiline: ["/*", "*/"]
	},
	snippets: {
		if: `if ($1) {
	$2
}`
	}
};

export const javascript = {
	id: "javascript",
	snippets: {
		log: "console.log($1)",
	}
};