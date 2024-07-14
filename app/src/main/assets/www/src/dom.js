/**
 * Get the text node at a given chracter offset
 */
export function getNode(offset, container) {
	var node, sum = 0;
	var walk = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);

	while (node = walk.nextNode()) {
		sum += node.data.length;

		if (sum >= offset) {
			return node;
		}
	}

	// if here, offset is larger than maximum
	return null;
}

/**
 * Get the character offset of a given node in the highlighted source
 */
export function getOffset (node, container) {
	var range = document.createRange();
	range.selectNodeContents(container);
	range.setEnd(node, 0);
	return range.toString().length;
}