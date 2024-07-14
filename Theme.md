# Instructions for Customizing CSS

Custom CSS is used to alter the display properties of the screen. Follow the instructions below to
customize the appearance of your application.

## Obtaining IDs or Relative Paths

Use the **Assistant Developer** application to obtain the IDs or a relative path to the item you
want to modify.

## Defining Properties

You can define various properties for interface elements. Here are some of the properties you can
use:

- **color**: Defines the color of the text or element.
- **background-image**: Defines the background image.
- **alpha**: Defines the opacity of the element.
- **color-tint**: Applies a color over an image.
- **color-filter**: Applies a color filter to the element.
- **display**: Defines the display of the element (use `none` to hide).

## Example of Custom CSS

```css
/* 
wallpaper = false
wallpaper_file = "wall.png"
wallpaper_alpha = 60
wallpaper_alpha_navigation = 75
wallpaper_alpha_toolbar = 60

change_colors = true
primary_color = #ff0000
secondary_color = 0
background_color = #151515

bubble_colors = true
bubble_right = #a00000
bubble_left = #0000a0
*/

#elementID {
    color: #FFFFFF;
    background-image: url('background.png');
    alpha: 0.8;
    color-tint: rgba(255, 0, 0, 0.5);
    color-filter: grayscale(100%);
    display: none;
}
```

### Explanation of the Example

- **Initial Comment**: The comment at the beginning of the CSS is used to define various settings.
    - `wallpaper = false`: Disables the use of a wallpaper.
    - `wallpaper_file = "wall.png"`: Defines the wallpaper image file.
    - `wallpaper_alpha = 60`: Defines the opacity of the wallpaper.
    - `wallpaper_alpha_navigation = 75`: Defines the opacity of the wallpaper in the navigation bar.
    - `wallpaper_alpha_toolbar = 60`: Defines the opacity of the wallpaper in the toolbar.
    - `change_colors = true`: Enables color changes.
    - `primary_color = color_system_accent1_300`: Defines the primary color.
    - `secondary_color = color_system_accent2_500`: Defines the secondary color.
    - `background_color = color_system_accent2_900`: Defines the background color.
    - `bubble_colors = true`: Enables color changes for bubbles.
    - `bubble_right = color_system_accent1_500`: Defines the color of the right bubble.
    - `bubble_left = color_system_accent3_500`: Defines the color of the left bubble.

### Using Material You Colors

To use the Material You colors defined by Android, you can use the prefix `color_` followed by the
name of the Material You ID. For example:

- `primary_color = color_system_accent1_300`
- `secondary_color = color_system_accent2_500`
- `background_color = color_system_accent2_900`
- `bubble_right = color_system_accent1_500`
- `bubble_left = color_system_accent3_500`

### Example with Material You Colors

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
background_color = color_system_accent2_900

bubble_colors = true
bubble_right = color_system_accent1_500
bubble_left = color_system_accent3_500
*/
```

Follow these instructions to customize the appearance of your application as needed.