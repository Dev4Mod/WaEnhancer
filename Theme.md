# Instructions for Customizing CSS

Custom CSS is used to alter the display properties of the screen. Follow the instructions below to
customize the appearance of your application.

## Obtaining IDs or Relative Paths

Use the **Assistant Developer** application to obtain the IDs or a relative path to the item you
want to modify.

## Defining Properties

You can define various properties for interface elements. Here are the properties you can use:

- **color**: Defines the color of the text or element.
- **background-color**: Changes the background color. Can use format
  `background-color: #original #replacement` to replace specific colors.
- **background-image**: Defines the background image, use `url('filename.png')`.
- **background-size**: Controls the size of background images. Can use specific values, percentages,
  or `cover`.
- **background**: Shorthand property for background settings. Supports colors, images and linear
  gradients.
- **foreground**: Sets the foreground drawable, supports colors and images.
- **alpha**: Defines the opacity of the element (0.0 to 1.0).
- **display**: Controls visibility (`none` to hide, `block` to show, `invisible` for invisible but
  taking space).
- **font-size**: Sets the text size for TextView elements.
- **width/height**: Sets element dimensions, supports pixels and percentage values.
- **left/right/top/bottom**: Sets element positioning within its parent container.
- **color-filter**: Applies a color filter to the element. Format: `color-filter: MODE #COLOR` (
  e.g., `SRC_ATOP #FF0000`).
- **color-tint**: Applies a color tint to the element. Use `none` to remove.
- **parent**: Changes the parent container of an element.

## Advanced Example of Custom CSS

```css
/* 
wallpaper = false
wallpaper_file = "wall.png"
wallpaper_alpha = 60
wallpaper_alpha_navigation = 75
wallpaper_alpha_toolbar = 60

change_colors = true
primary_color = color_system_accent1_300
secondary_color = color_system_accent2_500
background_color = #151515

bubble_colors = true
bubble_right = #a00000
bubble_left = #0000a0

change_dpi = 420
*/

/* Target specific class with ID */
.com.whatsapp.HomeActivity #conversations_row_contact_name {
    color: #FFFFFF;
    font-size: 16px;
}

/* Simple element styling by ID */
#elementID {
    background-image: url('background.png');
    background-size: 100% 100%;
    alpha: 0.8;
    display: block;
}

/* Element with color tinting */
#imageView {
    color-tint: #FF0000 #00FF00 #0000FF;  /* normal, pressed, disabled states */
}

/* Element with positioning */
#bottomElement {
    width: 100%;
    height: 60px;
    bottom: 0px;
}

/* Using linear gradient background */
#gradientElement {
    background: linear-gradient(45deg, #FF0000 0%, #00FF00 50%, #0000FF 100%);
}

/* Replace specific background colors */
#coloredElement {
    background-color: #123456 #654321;  /* replace #123456 with #654321 */
}

/* Hiding elements */
#unwantedElement {
    display: none;
}
```

## Using Material You Colors

To use the Material You colors defined by Android, you can use the prefix color_ followed by the
name of the Material You ID. For example:

* primary_color = color_system_accent1_300
* secondary_color = color_system_accent2_500
* background_color = color_system_accent2_900
* bubble_right = color_system_accent1_500
* bubble_left = color_system_accent3_500

## Setting Custom DPI

You can set a custom DPI value to adjust the scaling of the entire UI:

```
/* 
change_dpi = 420
*/
```

## CSS Selectors

You can target elements using different selectors:

* Direct ID targeting: #elementID
* Class with ID targeting: com.whatsapp.ClassName #elementID
* Element type with attributes: TextView:nth-child(1) or ImageView:contains(text)

Follow these instructions to customize the appearance of your application as needed.