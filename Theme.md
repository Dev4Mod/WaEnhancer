# Customization Guide for WhatsApp Enhancer

Custom CSS allows you to modify the appearance of the application, including colors, layouts, and
visibility of elements. This guide details the available customization options supported by the
`CustomView` engine.

## 1. Global Configuration

Global settings must be defined in a comment block `/* ... */` at the very beginning of your CSS
file. These properties are processed separately from standard CSS rules.

```css
/* 
author = "Your Name"
wallpaper = false / true
wallpaper_file = "wall.png"
wallpaper_alpha = 60
wallpaper_alpha_navigation = 75
wallpaper_alpha_toolbar = 60

change_colors = true
primary_color = #FF0000
secondary_color = #00FF00
background_color = #151515

bubble_colors = true
bubble_right = #a00000
bubble_left = #0000a0

change_dpi = 420
*/
```

### Supported Global Keys

- **change_dpi**: Sets a custom DPI for the application UI.
- **change_colors**: Enable/Disable general color replacements.
- **primary_color / secondary_color / background_color**: Define theme colors. Support Hex codes or
  Material You names (see below).
- **bubble_colors**: Enable/Disable chat bubble coloring.
- **bubble_right / bubble_left**: specific colors for chat bubbles.

## 2. Selectors

You can target elements in the UI using the following selector patterns:

| Pattern          | Description                                               | Example                           |
|:-----------------|:----------------------------------------------------------|:----------------------------------|
| **ID**           | Targets a view with a specific Resource ID.               | `#conversation_row_date`          |
| **Class + ID**   | Targets an ID only within a specific Java class/Activity. | `.com.whatsapp.HomeActivity #fab` |
| **Android ID**   | Targets system android IDs.                               | `#android_message`                |
| **Pseudo-class** | Advanced filtering by state or content.                   | `TextView:contains(WhatsApp)`     |

### Pseudo-selectors

- **:nth-child(n)**: Targets the *n*-th child of a parent (e.g., `TextView:nth-child(2)`).
- **:contains(text)**: Targets elements containing specific text (e.g.,
  `TextView:contains(Archived)`).
- **Widgets**: You can also target generic widget classes like `TextView`, `ImageView`,
  `LinearLayout`.

## 3. CSS Properties

The following properties can be used inside your CSS rules.

### Layout & Visibility

| Property                        | Description                                        | Values                                                                             |
|:--------------------------------|:---------------------------------------------------|:-----------------------------------------------------------------------------------|
| **parent**                      | Moves the element to a different parent container. | `root` (moves to top), `#id` (moves inside specific view)                          |
| **display**                     | Controls visibility.                               | `none` (gone), `block` (visible), `invisible` (hidden but takes space)             |
| **width**                       | Sets the width.                                    | `100%`, `50px` (treated as dp), `200` (raw pixels)                                 |
| **height**                      | Sets the height.                                   | `100%`, `50px` (treated as dp), `200` (raw pixels)                                 |
| **alpha**                       | Transparency level.                                | `0.0` to `1.0`                                                                     |
| **left / right / top / bottom** | Sets margins or relative layout alignment.         | `16px`                                                                             |
| **margin**                      | Sets outer spacing between elements.               | `16px` (all), `10px 20px` (Vert Horz), `10px 5px 10px 0px` (Top Right Bottom Left) |
| **margin-left / -right / ...**  | Sets specific margin side.                         | `16px`                                                                             |
| **padding**                     | Sets inner spacing within the element.             | `16px` (all), `10px 20px` (Vert Horz), `10px 5px 10px 0px` (Top Right Bottom Left) |
| **padding-left / -right / ...** | Sets specific padding side.                        | `16px`                                                                             |

### Backgrounds & Images

| Property             | Description                             | Example                                                    |
|:---------------------|:----------------------------------------|:-----------------------------------------------------------|
| **background**       | Sets a solid color, image, or gradient. | `#FF0000`, `url('bg.png')`, `linear-gradient(...)`, `none` |
| **background-image** | Sets the background image.              | `url('filename.png')`                                      |
| **foreground**       | Sets the foreground drawable.           | `#0000FF`, `url('fg.png')`                                 |
| **background-size**  | Scales the background image.            | `100% 100%`, `cover`, `50px 50px`                          |

**Gradient Format:**
`linear-gradient(angle, color1 location%, color2 location%, ...)`
Example: `linear-gradient(45deg, #FF0000 0%, #00FF00 100%)`

### Color Manipulation

| Property             | Description                                                   | Example                                                               |
|:---------------------|:--------------------------------------------------------------|:----------------------------------------------------------------------|
| **background-color** | **REPLACES** specific colors in the view (Background & Tint). | `#oldColor #newColor`                                                 |
| **color-tint**       | Tints the image or background.                                | `#FF0000` (single), `#Normal #Pressed #Disabled` (state list), `none` |
| **color-filter**     | Applies a PorterDuff Color Filter.                            | `SRC_ATOP #FF0000`, `none`                                            |

### Typography (TextViews only)

| Property      | Description          | Example                                      |
|:--------------|:---------------------|:---------------------------------------------|
| **color**     | Sets the text color. | `#FFFFFF`                                    |
| **font-size** | Sets the text size.  | `16px` (treated as scale-independent pixels) |

## 4. Values & Units

### Colors

- **Hex**: `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`.
- **Material You**: Use the `color_` prefix followed by the system name.
    - Examples: `color_system_accent1_100`, `color_system_neutral1_900`.

### Dimensions

- **px**: In this engine, values with the `px` unit are converted to device-dependent pixels (dp).
  `10px` behaves like `10dp` in Android layout XML coverage.
- **%**: Percentage relative to parent or screen depending on context.
- **Raw number**: Interpreted as raw pixels.

## 5. Examples

```css
/* 
change_dpi = 380 
primary_color = #1F1F1F
*/

/* Change FAB color */
#fab {
    background-color: #00AA00 #FF5500; /* Replaces green with orange */
    color-tint: #FFFFFF;
}

/* Customize Conversation Row Name */
.com.whatsapp.HomeActivity #conversations_row_contact_name {
    color: #E0E0E0;
    font-size: 18px;
}

/* Move Date to Bottom */
#conversation_row_date {
    parent: #conversation_contact_name_holder;
    bottom: 0px;
    right: 16px;
    font-size: 12px;
}

/* Specific background for main content */
#content {
    background: linear-gradient(0deg, #101010 0%, #202020 100%);
}

/* Hide archived chat hint */
TextView:contains(Archived) {
    display: none;
}
```

## Obtaining IDs

Use the **Assistant Developer** application/tool to inspect the layout and find the resource IDs (
`#id`) or class names of the elements you wish to customize.
