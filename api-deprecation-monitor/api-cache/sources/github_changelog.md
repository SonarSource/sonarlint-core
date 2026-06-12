<!doctype html>
<html lang="en-US" class="mt-0">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<link rel="profile" href="https://gmpg.org/xfn/11">
	<link rel="icon" type="image/x-icon" href="https://github.githubassets.com/favicon.ico">
	<meta name='robots' content='index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1' />

	<!-- This site is optimized with the Yoast SEO Premium plugin v27.7 (Yoast SEO v27.7) - https://yoast.com/product/yoast-seo-premium-wordpress/ -->
	<title>GitHub Changelog</title>
	<meta name="description" content="Your source for the latest features, improvements, and fixes across GitHub products, all in one place." />
	<link rel="canonical" href="https://github.blog/changelog/" />
	<link rel="next" href="https://github.blog/changelog/page/2/" />
	<meta property="og:locale" content="en_US" />
	<meta property="og:type" content="website" />
	<meta property="og:title" content="GitHub Changelog" />
	<meta property="og:url" content="https://github.blog/changelog/" />
	<meta property="og:site_name" content="The GitHub Blog" />
	<meta property="og:image" content="https://github.blog/wp-content/themes/github-2021-child/dist/img/social-v3-changelog.jpg" />
	<meta name="twitter:card" content="summary_large_image" />
	<script type="application/ld+json" class="yoast-schema-graph">{"@context":"https:\/\/schema.org","@graph":[{"@type":"CollectionPage","@id":"https:\/\/github.blog\/changelog\/","url":"https:\/\/github.blog\/changelog\/","name":"Changelogs Archive - The GitHub Blog","isPartOf":{"@id":"https:\/\/github.blog\/#website"},"breadcrumb":{"@id":"https:\/\/github.blog\/changelog\/#breadcrumb"},"inLanguage":"en-US"},{"@type":"BreadcrumbList","@id":"https:\/\/github.blog\/changelog\/#breadcrumb","itemListElement":[{"@type":"ListItem","position":1,"name":"Home","item":"https:\/\/github.blog\/"},{"@type":"ListItem","position":2,"name":"Changelogs"}]},{"@type":"WebSite","@id":"https:\/\/github.blog\/#website","url":"https:\/\/github.blog\/","name":"The GitHub Blog","description":"Updates, ideas, and inspiration from GitHub to help developers build and design software.","potentialAction":[{"@type":"SearchAction","target":{"@type":"EntryPoint","urlTemplate":"https:\/\/github.blog\/?s={search_term_string}"},"query-input":{"@type":"PropertyValueSpecification","valueRequired":true,"valueName":"search_term_string"}}],"inLanguage":"en-US"}]}</script>
	<!-- / Yoast SEO Premium plugin. -->


<link rel='dns-prefetch' href='//ghcc.githubassets.com' />
<link rel='dns-prefetch' href='//js.monitor.azure.com' />
<link rel='dns-prefetch' href='//analytics.githubassets.com' />
<link rel='dns-prefetch' href='//stats.wp.com' />
<link rel='dns-prefetch' href='//v0.wordpress.com' />
<link rel="alternate" type="application/rss+xml" title="The GitHub Blog &raquo; Feed" href="https://github.blog/feed/" />
<link rel="alternate" type="application/rss+xml" title="The GitHub Blog &raquo; Comments Feed" href="https://github.blog/comments/feed/" />
<link rel="alternate" type="application/rss+xml" title="The GitHub Blog &raquo; Changelogs Feed" href="https://github.blog/changelog/feed/" />
<style id='wp-img-auto-sizes-contain-inline-css'>
img:is([sizes=auto i],[sizes^="auto," i]){contain-intrinsic-size:3000px 1500px}
/*# sourceURL=wp-img-auto-sizes-contain-inline-css */
</style>
<style id='wp-emoji-styles-inline-css'>

	img.wp-smiley, img.emoji {
		display: inline !important;
		border: none !important;
		box-shadow: none !important;
		height: 1em !important;
		width: 1em !important;
		margin: 0 0.07em !important;
		vertical-align: -0.1em !important;
		background: none !important;
		padding: 0 !important;
	}
/*# sourceURL=wp-emoji-styles-inline-css */
</style>
<style id='wp-block-library-inline-css'>
:root{--wp-block-synced-color:#7a00df;--wp-block-synced-color--rgb:122,0,223;--wp-bound-block-color:var(--wp-block-synced-color);--wp-editor-canvas-background:#ddd;--wp-admin-theme-color:#007cba;--wp-admin-theme-color--rgb:0,124,186;--wp-admin-theme-color-darker-10:#006ba1;--wp-admin-theme-color-darker-10--rgb:0,107,160.5;--wp-admin-theme-color-darker-20:#005a87;--wp-admin-theme-color-darker-20--rgb:0,90,135;--wp-admin-border-width-focus:2px}@media (min-resolution:192dpi){:root{--wp-admin-border-width-focus:1.5px}}.wp-element-button{cursor:pointer}:root .has-very-light-gray-background-color{background-color:#eee}:root .has-very-dark-gray-background-color{background-color:#313131}:root .has-very-light-gray-color{color:#eee}:root .has-very-dark-gray-color{color:#313131}:root .has-vivid-green-cyan-to-vivid-cyan-blue-gradient-background{background:linear-gradient(135deg,#00d084,#0693e3)}:root .has-purple-crush-gradient-background{background:linear-gradient(135deg,#34e2e4,#4721fb 50%,#ab1dfe)}:root .has-hazy-dawn-gradient-background{background:linear-gradient(135deg,#faaca8,#dad0ec)}:root .has-subdued-olive-gradient-background{background:linear-gradient(135deg,#fafae1,#67a671)}:root .has-atomic-cream-gradient-background{background:linear-gradient(135deg,#fdd79a,#004a59)}:root .has-nightshade-gradient-background{background:linear-gradient(135deg,#330968,#31cdcf)}:root .has-midnight-gradient-background{background:linear-gradient(135deg,#020381,#2874fc)}:root{--wp--preset--font-size--normal:16px;--wp--preset--font-size--huge:42px}.has-regular-font-size{font-size:1em}.has-larger-font-size{font-size:2.625em}.has-normal-font-size{font-size:var(--wp--preset--font-size--normal)}.has-huge-font-size{font-size:var(--wp--preset--font-size--huge)}.has-text-align-center{text-align:center}.has-text-align-left{text-align:left}.has-text-align-right{text-align:right}.has-fit-text{white-space:nowrap!important}#end-resizable-editor-section{display:none}.aligncenter{clear:both}.items-justified-left{justify-content:flex-start}.items-justified-center{justify-content:center}.items-justified-right{justify-content:flex-end}.items-justified-space-between{justify-content:space-between}.screen-reader-text{border:0;clip-path:inset(50%);height:1px;margin:-1px;overflow:hidden;padding:0;position:absolute;width:1px;word-wrap:normal!important}.screen-reader-text:focus{background-color:#ddd;clip-path:none;color:#444;display:block;font-size:1em;height:auto;left:5px;line-height:normal;padding:15px 23px 14px;text-decoration:none;top:5px;width:auto;z-index:100000}html :where(.has-border-color){border-style:solid}html :where([style*=border-top-color]){border-top-style:solid}html :where([style*=border-right-color]){border-right-style:solid}html :where([style*=border-bottom-color]){border-bottom-style:solid}html :where([style*=border-left-color]){border-left-style:solid}html :where([style*=border-width]){border-style:solid}html :where([style*=border-top-width]){border-top-style:solid}html :where([style*=border-right-width]){border-right-style:solid}html :where([style*=border-bottom-width]){border-bottom-style:solid}html :where([style*=border-left-width]){border-left-style:solid}html :where(img[class*=wp-image-]){height:auto;max-width:100%}:where(figure){margin:0 0 1em}html :where(.is-position-sticky){--wp-admin--admin-bar--position-offset:var(--wp-admin--admin-bar--height,0px)}@media screen and (max-width:600px){html :where(.is-position-sticky){--wp-admin--admin-bar--position-offset:0px}}

/*# sourceURL=wp-block-library-inline-css */
</style>
<style id='github-typography-presets-inline-css'>
.is-typography-preset-h1{font-size:var(--base-size-36, 36px);font-weight:700;line-height:1.25;margin-block-end:20px;margin-block-start:0;}.is-typography-preset-h2{font-size:var(--base-size-32, 32px);font-weight:700;line-height:1.25;margin-block-end:20px;margin-block-start:40px;}.is-typography-preset-h3{font-size:var(--base-size-28, 28px);font-weight:700;line-height:1.25;margin-block-end:12px;margin-block-start:20px;}.is-typography-preset-h4{font-size:var(--base-size-24, 24px);font-weight:700;line-height:1.25;margin-block-end:12px;margin-block-start:20px;}.is-typography-preset-h5{font-size:var(--base-size-20, 20px);font-weight:700;line-height:1.25;margin-block-end:12px;margin-block-start:20px;}.is-typography-preset-h6{font-size:var(--base-size-18, 18px);font-weight:700;line-height:1.25;margin-block-end:0;margin-block-start:20px;}.is-typography-preset-display{font:var(--text-display-shorthand);}.is-typography-preset-title-large{font:var(--text-title-shorthand-large);}.is-typography-preset-title-medium{font:var(--text-title-shorthand-medium);}.is-typography-preset-title-small{font:var(--text-title-shorthand-small);}.is-typography-preset-subtitle{font:var(--text-subtitle-shorthand);}.is-typography-preset-body-large{font:var(--text-body-shorthand-large);}.is-typography-preset-body-medium{font:var(--text-body-shorthand-medium);}.is-typography-preset-body-small{font:var(--text-body-shorthand-small);}
/*# sourceURL=github-typography-presets-inline-css */
</style>
<style id='global-styles-inline-css'>
:root{--wp--preset--aspect-ratio--square: 1;--wp--preset--aspect-ratio--4-3: 4/3;--wp--preset--aspect-ratio--3-4: 3/4;--wp--preset--aspect-ratio--3-2: 3/2;--wp--preset--aspect-ratio--2-3: 2/3;--wp--preset--aspect-ratio--16-9: 16/9;--wp--preset--aspect-ratio--9-16: 9/16;--wp--preset--color--black: #000000;--wp--preset--color--cyan-bluish-gray: #abb8c3;--wp--preset--color--white: #ffffff;--wp--preset--color--pale-pink: #f78da7;--wp--preset--color--vivid-red: #cf2e2e;--wp--preset--color--luminous-vivid-orange: #ff6900;--wp--preset--color--luminous-vivid-amber: #fcb900;--wp--preset--color--light-green-cyan: #7bdcb5;--wp--preset--color--vivid-green-cyan: #00d084;--wp--preset--color--pale-cyan-blue: #8ed1fc;--wp--preset--color--vivid-cyan-blue: #0693e3;--wp--preset--color--vivid-purple: #9b51e0;--wp--preset--gradient--vivid-cyan-blue-to-vivid-purple: linear-gradient(135deg,rgb(6,147,227) 0%,rgb(155,81,224) 100%);--wp--preset--gradient--light-green-cyan-to-vivid-green-cyan: linear-gradient(135deg,rgb(122,220,180) 0%,rgb(0,208,130) 100%);--wp--preset--gradient--luminous-vivid-amber-to-luminous-vivid-orange: linear-gradient(135deg,rgb(252,185,0) 0%,rgb(255,105,0) 100%);--wp--preset--gradient--luminous-vivid-orange-to-vivid-red: linear-gradient(135deg,rgb(255,105,0) 0%,rgb(207,46,46) 100%);--wp--preset--gradient--very-light-gray-to-cyan-bluish-gray: linear-gradient(135deg,rgb(238,238,238) 0%,rgb(169,184,195) 100%);--wp--preset--gradient--cool-to-warm-spectrum: linear-gradient(135deg,rgb(74,234,220) 0%,rgb(151,120,209) 20%,rgb(207,42,186) 40%,rgb(238,44,130) 60%,rgb(251,105,98) 80%,rgb(254,248,76) 100%);--wp--preset--gradient--blush-light-purple: linear-gradient(135deg,rgb(255,206,236) 0%,rgb(152,150,240) 100%);--wp--preset--gradient--blush-bordeaux: linear-gradient(135deg,rgb(254,205,165) 0%,rgb(254,45,45) 50%,rgb(107,0,62) 100%);--wp--preset--gradient--luminous-dusk: linear-gradient(135deg,rgb(255,203,112) 0%,rgb(199,81,192) 50%,rgb(65,88,208) 100%);--wp--preset--gradient--pale-ocean: linear-gradient(135deg,rgb(255,245,203) 0%,rgb(182,227,212) 50%,rgb(51,167,181) 100%);--wp--preset--gradient--electric-grass: linear-gradient(135deg,rgb(202,248,128) 0%,rgb(113,206,126) 100%);--wp--preset--gradient--midnight: linear-gradient(135deg,rgb(2,3,129) 0%,rgb(40,116,252) 100%);--wp--preset--font-size--small: 13px;--wp--preset--font-size--medium: 20px;--wp--preset--font-size--large: 36px;--wp--preset--font-size--x-large: 42px;--wp--preset--spacing--20: var(--base-size-20, 1.25rem);--wp--preset--spacing--30: 0.67rem;--wp--preset--spacing--40: var(--base-size-40, 2.5rem);--wp--preset--spacing--50: 1.5rem;--wp--preset--spacing--60: 2.25rem;--wp--preset--spacing--70: 3.38rem;--wp--preset--spacing--80: var(--base-size-80, 5rem);--wp--preset--spacing--4: var(--base-size-4, 0.25rem);--wp--preset--spacing--6: var(--base-size-6, 0.375rem);--wp--preset--spacing--8: var(--base-size-8, 0.5rem);--wp--preset--spacing--2: var(--base-size-2, 0.125rem);--wp--preset--spacing--12: var(--base-size-12, 0.75rem);--wp--preset--spacing--16: var(--base-size-16, 1rem);--wp--preset--spacing--24: var(--base-size-24, 1.5rem);--wp--preset--spacing--28: var(--base-size-28, 1.75rem);--wp--preset--spacing--32: var(--base-size-32, 2rem);--wp--preset--spacing--36: var(--base-size-36, 2.25rem);--wp--preset--spacing--44: var(--base-size-44, 2.75rem);--wp--preset--spacing--48: var(--base-size-48, 3rem);--wp--preset--spacing--64: var(--base-size-64, 4rem);--wp--preset--spacing--96: var(--base-size-96, 6rem);--wp--preset--spacing--112: var(--base-size-112, 7rem);--wp--preset--spacing--128: var(--base-size-128, 8rem);--wp--preset--shadow--natural: 6px 6px 9px rgba(0, 0, 0, 0.2);--wp--preset--shadow--deep: 12px 12px 50px rgba(0, 0, 0, 0.4);--wp--preset--shadow--sharp: 6px 6px 0px rgba(0, 0, 0, 0.2);--wp--preset--shadow--outlined: 6px 6px 0px -3px rgb(255, 255, 255), 6px 6px rgb(0, 0, 0);--wp--preset--shadow--crisp: 6px 6px 0px rgb(0, 0, 0);--wp--custom--typography--presets--0--name: Like h1;--wp--custom--typography--presets--0--slug: h1;--wp--custom--typography--presets--0--font-size: var(--base-size-36, 36px);--wp--custom--typography--presets--0--font-weight: 700;--wp--custom--typography--presets--0--line-height: 1.25;--wp--custom--typography--presets--0--margin-block-end: 20px;--wp--custom--typography--presets--0--margin-block-start: 0;--wp--custom--typography--presets--1--name: Like h2;--wp--custom--typography--presets--1--slug: h2;--wp--custom--typography--presets--1--font-size: var(--base-size-32, 32px);--wp--custom--typography--presets--1--font-weight: 700;--wp--custom--typography--presets--1--line-height: 1.25;--wp--custom--typography--presets--1--margin-block-end: 20px;--wp--custom--typography--presets--1--margin-block-start: 40px;--wp--custom--typography--presets--2--name: Like h3;--wp--custom--typography--presets--2--slug: h3;--wp--custom--typography--presets--2--font-size: var(--base-size-28, 28px);--wp--custom--typography--presets--2--font-weight: 700;--wp--custom--typography--presets--2--line-height: 1.25;--wp--custom--typography--presets--2--margin-block-end: 12px;--wp--custom--typography--presets--2--margin-block-start: 20px;--wp--custom--typography--presets--3--name: Like h4;--wp--custom--typography--presets--3--slug: h4;--wp--custom--typography--presets--3--font-size: var(--base-size-24, 24px);--wp--custom--typography--presets--3--font-weight: 700;--wp--custom--typography--presets--3--line-height: 1.25;--wp--custom--typography--presets--3--margin-block-end: 12px;--wp--custom--typography--presets--3--margin-block-start: 20px;--wp--custom--typography--presets--4--name: Like h5;--wp--custom--typography--presets--4--slug: h5;--wp--custom--typography--presets--4--font-size: var(--base-size-20, 20px);--wp--custom--typography--presets--4--font-weight: 700;--wp--custom--typography--presets--4--line-height: 1.25;--wp--custom--typography--presets--4--margin-block-end: 12px;--wp--custom--typography--presets--4--margin-block-start: 20px;--wp--custom--typography--presets--5--name: Like h6;--wp--custom--typography--presets--5--slug: h6;--wp--custom--typography--presets--5--font-size: var(--base-size-18, 18px);--wp--custom--typography--presets--5--font-weight: 700;--wp--custom--typography--presets--5--line-height: 1.25;--wp--custom--typography--presets--5--margin-block-end: 0;--wp--custom--typography--presets--5--margin-block-start: 20px;--wp--custom--typography--presets--6--name: Display;--wp--custom--typography--presets--6--slug: display;--wp--custom--typography--presets--6--font: var(--text-display-shorthand);--wp--custom--typography--presets--7--name: Title Large;--wp--custom--typography--presets--7--slug: title-large;--wp--custom--typography--presets--7--font: var(--text-title-shorthand-large);--wp--custom--typography--presets--8--name: Title Medium;--wp--custom--typography--presets--8--slug: title-medium;--wp--custom--typography--presets--8--font: var(--text-title-shorthand-medium);--wp--custom--typography--presets--9--name: Title Small;--wp--custom--typography--presets--9--slug: title-small;--wp--custom--typography--presets--9--font: var(--text-title-shorthand-small);--wp--custom--typography--presets--10--name: Subtitle;--wp--custom--typography--presets--10--slug: subtitle;--wp--custom--typography--presets--10--font: var(--text-subtitle-shorthand);--wp--custom--typography--presets--11--name: Body Large;--wp--custom--typography--presets--11--slug: body-large;--wp--custom--typography--presets--11--font: var(--text-body-shorthand-large);--wp--custom--typography--presets--12--name: Body Medium;--wp--custom--typography--presets--12--slug: body-medium;--wp--custom--typography--presets--12--font: var(--text-body-shorthand-medium);--wp--custom--typography--presets--13--name: Body Small;--wp--custom--typography--presets--13--slug: body-small;--wp--custom--typography--presets--13--font: var(--text-body-shorthand-small);}:root { --wp--style--global--content-size: calc(1280px - var(--p-responsive-blog) * 2);--wp--style--global--wide-size: calc(1280px - var(--p-responsive-blog) * 2); }:where(body) { margin: 0; }.wp-site-blocks { padding-top: var(--wp--style--root--padding-top); padding-bottom: var(--wp--style--root--padding-bottom); }.has-global-padding { padding-right: var(--wp--style--root--padding-right); padding-left: var(--wp--style--root--padding-left); }.has-global-padding > .alignfull { margin-right: calc(var(--wp--style--root--padding-right) * -1); margin-left: calc(var(--wp--style--root--padding-left) * -1); }.has-global-padding :where(:not(.alignfull.is-layout-flow) > .has-global-padding:not(.wp-block-block, .alignfull)) { padding-right: 0; padding-left: 0; }.has-global-padding :where(:not(.alignfull.is-layout-flow) > .has-global-padding:not(.wp-block-block, .alignfull)) > .alignfull { margin-left: 0; margin-right: 0; }.wp-site-blocks > .alignleft { float: left; margin-right: 2em; }.wp-site-blocks > .alignright { float: right; margin-left: 2em; }.wp-site-blocks > .aligncenter { justify-content: center; margin-left: auto; margin-right: auto; }:where(.wp-site-blocks) > * { margin-block-start: 24px; margin-block-end: 0; }:where(.wp-site-blocks) > :first-child { margin-block-start: 0; }:where(.wp-site-blocks) > :last-child { margin-block-end: 0; }:root { --wp--style--block-gap: 24px; }:root :where(.is-layout-flow) > :first-child{margin-block-start: 0;}:root :where(.is-layout-flow) > :last-child{margin-block-end: 0;}:root :where(.is-layout-flow) > *{margin-block-start: 24px;margin-block-end: 0;}:root :where(.is-layout-constrained) > :first-child{margin-block-start: 0;}:root :where(.is-layout-constrained) > :last-child{margin-block-end: 0;}:root :where(.is-layout-constrained) > *{margin-block-start: 24px;margin-block-end: 0;}:root :where(.is-layout-flex){gap: 24px;}:root :where(.is-layout-grid){gap: 24px;}.is-layout-flow > .alignleft{float: left;margin-inline-start: 0;margin-inline-end: 2em;}.is-layout-flow > .alignright{float: right;margin-inline-start: 2em;margin-inline-end: 0;}.is-layout-flow > .aligncenter{margin-left: auto !important;margin-right: auto !important;}.is-layout-constrained > .alignleft{float: left;margin-inline-start: 0;margin-inline-end: 2em;}.is-layout-constrained > .alignright{float: right;margin-inline-start: 2em;margin-inline-end: 0;}.is-layout-constrained > .aligncenter{margin-left: auto !important;margin-right: auto !important;}.is-layout-constrained > :where(:not(.alignleft):not(.alignright):not(.alignfull)){max-width: var(--wp--style--global--content-size);margin-left: auto !important;margin-right: auto !important;}.is-layout-constrained > .alignwide{max-width: var(--wp--style--global--wide-size);}body .is-layout-flex{display: flex;}.is-layout-flex{flex-wrap: wrap;align-items: center;}.is-layout-flex > :is(*, div){margin: 0;}body .is-layout-grid{display: grid;}.is-layout-grid > :is(*, div){margin: 0;}body{font-family: var(--brand-fontStack-sansSerif, sans-serif);--wp--style--root--padding-top: 0px;--wp--style--root--padding-right: var(--p-responsive-blog);--wp--style--root--padding-bottom: 0px;--wp--style--root--padding-left: var(--p-responsive-blog);}a:where(:not(.wp-element-button)){text-decoration: underline;}:root :where(.wp-element-button, .wp-block-button__link){background-color: #32373c;border-width: 0;color: #fff;font-family: inherit;font-size: inherit;font-style: inherit;font-weight: inherit;letter-spacing: inherit;line-height: inherit;padding-top: calc(0.667em + 2px);padding-right: calc(1.333em + 2px);padding-bottom: calc(0.667em + 2px);padding-left: calc(1.333em + 2px);text-decoration: none;text-transform: inherit;}.has-black-color{color: var(--wp--preset--color--black) !important;}.has-cyan-bluish-gray-color{color: var(--wp--preset--color--cyan-bluish-gray) !important;}.has-white-color{color: var(--wp--preset--color--white) !important;}.has-pale-pink-color{color: var(--wp--preset--color--pale-pink) !important;}.has-vivid-red-color{color: var(--wp--preset--color--vivid-red) !important;}.has-luminous-vivid-orange-color{color: var(--wp--preset--color--luminous-vivid-orange) !important;}.has-luminous-vivid-amber-color{color: var(--wp--preset--color--luminous-vivid-amber) !important;}.has-light-green-cyan-color{color: var(--wp--preset--color--light-green-cyan) !important;}.has-vivid-green-cyan-color{color: var(--wp--preset--color--vivid-green-cyan) !important;}.has-pale-cyan-blue-color{color: var(--wp--preset--color--pale-cyan-blue) !important;}.has-vivid-cyan-blue-color{color: var(--wp--preset--color--vivid-cyan-blue) !important;}.has-vivid-purple-color{color: var(--wp--preset--color--vivid-purple) !important;}.has-black-background-color{background-color: var(--wp--preset--color--black) !important;}.has-cyan-bluish-gray-background-color{background-color: var(--wp--preset--color--cyan-bluish-gray) !important;}.has-white-background-color{background-color: var(--wp--preset--color--white) !important;}.has-pale-pink-background-color{background-color: var(--wp--preset--color--pale-pink) !important;}.has-vivid-red-background-color{background-color: var(--wp--preset--color--vivid-red) !important;}.has-luminous-vivid-orange-background-color{background-color: var(--wp--preset--color--luminous-vivid-orange) !important;}.has-luminous-vivid-amber-background-color{background-color: var(--wp--preset--color--luminous-vivid-amber) !important;}.has-light-green-cyan-background-color{background-color: var(--wp--preset--color--light-green-cyan) !important;}.has-vivid-green-cyan-background-color{background-color: var(--wp--preset--color--vivid-green-cyan) !important;}.has-pale-cyan-blue-background-color{background-color: var(--wp--preset--color--pale-cyan-blue) !important;}.has-vivid-cyan-blue-background-color{background-color: var(--wp--preset--color--vivid-cyan-blue) !important;}.has-vivid-purple-background-color{background-color: var(--wp--preset--color--vivid-purple) !important;}.has-black-border-color{border-color: var(--wp--preset--color--black) !important;}.has-cyan-bluish-gray-border-color{border-color: var(--wp--preset--color--cyan-bluish-gray) !important;}.has-white-border-color{border-color: var(--wp--preset--color--white) !important;}.has-pale-pink-border-color{border-color: var(--wp--preset--color--pale-pink) !important;}.has-vivid-red-border-color{border-color: var(--wp--preset--color--vivid-red) !important;}.has-luminous-vivid-orange-border-color{border-color: var(--wp--preset--color--luminous-vivid-orange) !important;}.has-luminous-vivid-amber-border-color{border-color: var(--wp--preset--color--luminous-vivid-amber) !important;}.has-light-green-cyan-border-color{border-color: var(--wp--preset--color--light-green-cyan) !important;}.has-vivid-green-cyan-border-color{border-color: var(--wp--preset--color--vivid-green-cyan) !important;}.has-pale-cyan-blue-border-color{border-color: var(--wp--preset--color--pale-cyan-blue) !important;}.has-vivid-cyan-blue-border-color{border-color: var(--wp--preset--color--vivid-cyan-blue) !important;}.has-vivid-purple-border-color{border-color: var(--wp--preset--color--vivid-purple) !important;}.has-vivid-cyan-blue-to-vivid-purple-gradient-background{background: var(--wp--preset--gradient--vivid-cyan-blue-to-vivid-purple) !important;}.has-light-green-cyan-to-vivid-green-cyan-gradient-background{background: var(--wp--preset--gradient--light-green-cyan-to-vivid-green-cyan) !important;}.has-luminous-vivid-amber-to-luminous-vivid-orange-gradient-background{background: var(--wp--preset--gradient--luminous-vivid-amber-to-luminous-vivid-orange) !important;}.has-luminous-vivid-orange-to-vivid-red-gradient-background{background: var(--wp--preset--gradient--luminous-vivid-orange-to-vivid-red) !important;}.has-very-light-gray-to-cyan-bluish-gray-gradient-background{background: var(--wp--preset--gradient--very-light-gray-to-cyan-bluish-gray) !important;}.has-cool-to-warm-spectrum-gradient-background{background: var(--wp--preset--gradient--cool-to-warm-spectrum) !important;}.has-blush-light-purple-gradient-background{background: var(--wp--preset--gradient--blush-light-purple) !important;}.has-blush-bordeaux-gradient-background{background: var(--wp--preset--gradient--blush-bordeaux) !important;}.has-luminous-dusk-gradient-background{background: var(--wp--preset--gradient--luminous-dusk) !important;}.has-pale-ocean-gradient-background{background: var(--wp--preset--gradient--pale-ocean) !important;}.has-electric-grass-gradient-background{background: var(--wp--preset--gradient--electric-grass) !important;}.has-midnight-gradient-background{background: var(--wp--preset--gradient--midnight) !important;}.has-small-font-size{font-size: var(--wp--preset--font-size--small) !important;}.has-medium-font-size{font-size: var(--wp--preset--font-size--medium) !important;}.has-large-font-size{font-size: var(--wp--preset--font-size--large) !important;}.has-x-large-font-size{font-size: var(--wp--preset--font-size--x-large) !important;}
/*# sourceURL=global-styles-inline-css */
</style>

<link rel='stylesheet' id='all-css-8' href='https://github.blog/_static/??-eJyNzMESwiAMBNAfMoZyoR4cvwVpCpkByjRhnP696MUeve7uW3w1CFtVqoot98hVcESFagcuPhJ6EVLBIKMoDOs+1iB6ZLqO7IKnA01USDCypv4Ea+yEC4t+rbASSNi56R8OQuK8/HRIvkbKW/zYR7lPbjY3a5yb3yJzRUs=' type='text/css' media='all' />
<link rel="https://api.w.org/" href="https://github.blog/wp-json/" /><link rel="EditURI" type="application/rsd+xml" title="RSD" href="https://github.blog/xmlrpc.php?rsd" />
<meta name="generator" content="WordPress 6.9.4" />
<!-- Stream WordPress user activity plugin v4.2.0 -->
	<style>img#wpstats{display:none}</style>
		<link rel="llms-sitemap" href="https://github.blog/llms.txt" />
<meta name="ha-url" content="https://collector.githubapp.com/github-blog/collect"><link rel="preload" href="https://github.blog/wp-content/themes/github-2021/dist/fonts/mona-sans.woff2" as="font" type="font/woff2" crossorigin="anonymous"><link rel="preload" href="https://github.blog/wp-content/themes/github-2021/dist/fonts/MonaspaceNeon-Regular.woff2" as="font" type="font/woff2" crossorigin="anonymous"><link rel="icon" href="https://github.blog/wp-content/uploads/2019/01/cropped-github-favicon-512.png?fit=32%2C32" sizes="32x32" />
<link rel="icon" href="https://github.blog/wp-content/uploads/2019/01/cropped-github-favicon-512.png?fit=192%2C192" sizes="192x192" />
<link rel="apple-touch-icon" href="https://github.blog/wp-content/uploads/2019/01/cropped-github-favicon-512.png?fit=180%2C180" />
<meta name="msapplication-TileImage" content="https://github.blog/wp-content/uploads/2019/01/cropped-github-favicon-512.png?fit=270%2C270" />
<style id='wp-style-engine-github-typography-presets-inline-css'>
.is-typography-preset-h1{font-size:var(--base-size-36, 36px);font-weight:700;line-height:1.25;margin-block-end:20px;margin-block-start:0;}.is-typography-preset-h2{font-size:var(--base-size-32, 32px);font-weight:700;line-height:1.25;margin-block-end:20px;margin-block-start:40px;}.is-typography-preset-h3{font-size:var(--base-size-28, 28px);font-weight:700;line-height:1.25;margin-block-end:12px;margin-block-start:20px;}.is-typography-preset-h4{font-size:var(--base-size-24, 24px);font-weight:700;line-height:1.25;margin-block-end:12px;margin-block-start:20px;}.is-typography-preset-h5{font-size:var(--base-size-20, 20px);font-weight:700;line-height:1.25;margin-block-end:12px;margin-block-start:20px;}.is-typography-preset-h6{font-size:var(--base-size-18, 18px);font-weight:700;line-height:1.25;margin-block-end:0;margin-block-start:20px;}.is-typography-preset-display{font:var(--text-display-shorthand);}.is-typography-preset-title-large{font:var(--text-title-shorthand-large);}.is-typography-preset-title-medium{font:var(--text-title-shorthand-medium);}.is-typography-preset-title-small{font:var(--text-title-shorthand-small);}.is-typography-preset-subtitle{font:var(--text-subtitle-shorthand);}.is-typography-preset-body-large{font:var(--text-body-shorthand-large);}.is-typography-preset-body-medium{font:var(--text-body-shorthand-medium);}.is-typography-preset-body-small{font:var(--text-body-shorthand-small);}
/*# sourceURL=wp-style-engine-github-typography-presets-inline-css */
</style>

</head>
<body class="archive post-type-archive post-type-archive-changelog wp-embed-responsive wp-theme-github-2021 wp-child-theme-github-2021-child font-mktg hfeed no-sidebar" data-color-mode="dark" data-light-theme="light" data-dark-theme="dark">
		<div data-color-mode="dark" data-light-theme="light" data-dark-theme="dark" class="pt-header pt-lg-0">
		<header id="header" class="header position-fixed position-lg-static pb-lg-header z-4 top-0 left-0 right-0 d-flex flex-column flex-items-stretch color-bg-default">
						<a href="#start-of-content" class="p-3 color-bg-accent-emphasis color-fg-on-emphasis show-on-focus">
				Skip to content			</a>
						<div class="position-relative container-xl width-full mx-auto p-responsive-blog">
				<div class="d-flex flex-items-center flex-justify-between pt-3 pb-3 color-fg-default">
					
<a href="https://github.com" target="_blank" rel="noreferrer" aria-label="GitHub homepage" class="Header-link position-relative d-flex flex-items-center color-fg-default">
	<svg aria-hidden="true" role="img" class="octicon octicon-mark-github d-block" viewBox="0 0 98 96" width="32" height="32" fill="currentColor"><g clip-path="url(#a)"><path d="M41.44 69.385C28.807 67.853 19.906 58.762 19.906 46.99c0-4.785 1.723-9.953 4.594-13.398-1.244-3.158-1.053-9.858.383-12.633 3.828-.479 8.996 1.531 12.058 4.307 3.637-1.149 7.465-1.723 12.155-1.723 4.69 0 8.517.574 11.963 1.627 2.966-2.68 8.23-4.69 12.058-4.211 1.34 2.584 1.531 9.283.287 12.537 3.063 3.637 4.69 8.518 4.69 13.494 0 11.772-8.9 20.672-21.725 22.3 3.254 2.104 5.455 6.698 5.455 11.962v9.953c0 2.871 2.393 4.498 5.264 3.35C84.41 87.95 98 70.629 98 49.19 98 22.107 75.988 0 48.904 0 21.82 0 0 22.107 0 49.191c0 21.246 13.494 38.856 31.678 45.46 2.584.956 5.072-.766 5.072-3.35v-7.657c-1.34.575-3.063.958-4.594.958-6.316 0-10.049-3.446-12.728-9.858-1.053-2.584-2.201-4.115-4.403-4.402-1.148-.096-1.53-.574-1.53-1.149 0-1.148 1.913-2.01 3.827-2.01 2.776 0 5.168 1.723 7.657 5.264 1.914 2.776 3.923 4.02 6.316 4.02 2.392 0 3.924-.861 6.125-3.063 1.627-1.627 2.871-3.062 4.02-4.02z" fill="#fff"/></g><defs><clipPath id="a"><path fill="#fff" d="M0 0h98v96H0z"/></clipPath></defs></svg>
</a>
<span class="d-inline-block ml-2 f1-mktg f2-md-mktg" style="opacity: 0.3;">/</span>
<a class="d-inline-block Header-link font-weight-semibold ml-2 f2 color-fg-default" href="https://github.blog/">
	Blog</a>
					
<nav class="d-none d-lg-block" aria-label="Secondary navigation">
	<ul id="secondary-navigation" class="secondary-navigation flex-items-center flex-nowrap list-style-none ml-4" aria-hidden="false"><li id="menu-item-78809"><a href="https://github.blog/changelog/" aria-current="page" class="position-relative d-flex flex-items-center flex-start no-wrap py-2 px-4 f4 lh-condensed-ultra Link--secondary color-fg-default text-medium"><span class="menu-item-label">Changelog</span></a></li>
<li id="menu-item-78810"><a href="https://docs.github.com/" class="position-relative d-flex flex-items-center flex-start no-wrap py-2 px-4 f4 lh-condensed-ultra Link--secondary color-fg-default text-medium"><span class="menu-item-label">Docs</span><svg viewBox="0 0 16 16" width="16" height="16" class="octicon octicon-chevron-down position-absolute right-0 d-block ml-1" role="presentation"><path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.854-1h4.146a.25.25 0 0 1 .25.25v4.146a.25.25 0 0 1-.427.177L13.03 4.03 9.28 7.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.75-3.75-1.543-1.543A.25.25 0 0 1 10.604 1Z"></path></svg></a></li>
<li id="menu-item-78811"><a href="https://github.com/customer-stories" class="position-relative d-flex flex-items-center flex-start no-wrap py-2 px-4 f4 lh-condensed-ultra Link--secondary color-fg-default text-medium"><span class="menu-item-label">Customer stories</span><svg viewBox="0 0 16 16" width="16" height="16" class="octicon octicon-chevron-down position-absolute right-0 d-block ml-1" role="presentation"><path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.854-1h4.146a.25.25 0 0 1 .25.25v4.146a.25.25 0 0 1-.427.177L13.03 4.03 9.28 7.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.75-3.75-1.543-1.543A.25.25 0 0 1 10.604 1Z"></path></svg></a></li>
</ul></nav>
					
<div class="d-none d-lg-flex flex-1">
	<form id="desktop-search" class="desktop-search position-relative ml-lg-4 flex-1" action="https://github.blog" method="get" aria-hidden="true" aria-label="Search form" role="search">
		<div class="position-relative d-flex flex-1 height-full color-bg-transparent" data-color-mode="light" data-light-theme="light" data-dark-theme="dark" >
			<input aria-label="Search the blog" type="search" class="p-2 pl-3 pr-6 border-0 rounded-2 flex-1" placeholder="Search the blog…" value="" name="s" id="search-input">
			<button type="submit" class="position-absolute right-0 z-3 d-flex flex-items-center flex-justify-center flex-self-center mr-2 p-2 border-0 rounded-2 color-bg-transparent color-fg-subtle" aria-label="Search">
				<svg viewBox="0 0 16 16" width="20" height="20" class="octicon octicon-search" role="presentation"><path fill-rule="evenodd" d="M11.5 7a4.499 4.499 0 11-8.998 0A4.499 4.499 0 0111.5 7zm-.82 4.74a6 6 0 111.06-1.06l3.04 3.04a.75.75 0 11-1.06 1.06l-3.04-3.04z"></path></svg>
			</button>
		</div>
	</form>
	<button aria-label="Toggle search" class="flex-self-center ml-auto p-2 border-0 color-bg-transparent color-fg-default rounded-3 js-toggle" aria-controls="desktop-search" aria-expanded="false" >
		<svg viewBox="0 0 24 24" width="24" height="24" class="octicon octicon-search" role="presentation"><path d="M10.25 2a8.25 8.25 0 0 1 6.34 13.53l5.69 5.69a.749.749 0 0 1-.326 1.275.749.749 0 0 1-.734-.215l-5.69-5.69A8.25 8.25 0 1 1 10.25 2ZM3.5 10.25a6.75 6.75 0 1 0 13.5 0 6.75 6.75 0 0 0-13.5 0Z"></path></svg>
		<svg viewBox="2 2 20 20" width="24" height="24" class="octicon octicon-x" role="presentation"><path d="M5.72 5.72a.75.75 0 0 1 1.06 0L12 10.94l5.22-5.22a.749.749 0 0 1 1.275.326.749.749 0 0 1-.215.734L13.06 12l5.22 5.22a.749.749 0 0 1-.326 1.275.749.749 0 0 1-.734-.215L12 13.06l-5.22 5.22a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042L10.94 12 5.72 6.78a.75.75 0 0 1 0-1.06Z"></path></svg>
	</button>

		<a		class="Button Button--size-medium Button--primary ml-3"
		href="https://github.com/features/copilot/cli"				target="_blank"		data-analytics-click="Blog, click on button, text: Use Copilot for free; ref_location:top nav;"					>
		<span class="Button__text">
		<span class="Text Text--200 Text--antialiased Text--weight-semibold Button--label Button--label-medium Button--label-primary">
			Try GitHub Copilot CLI		</span>
		</span>

			</a>
		<a		class="Button Button--size-medium Button--secondary ml-3"
		href="https://githubuniverse.com/"				target="_blank"		data-analytics-click="Blog, click on button, text: Attend Universe; ref_location:top nav;"					>
		<span class="Button__text">
		<span class="Text Text--200 Text--antialiased Text--weight-semibold Button--label Button--label-medium Button--label-secondary">
			Attend GitHub Universe		</span>
		</span>

			</a>
	</div>
					
<div class="d-flex d-lg-none flex-items-center flex-1 mr-n2">
	<button aria-label="Toggle search" class="ml-auto p-2 border-0 color-bg-transparent color-fg-default rounded-3 js-toggle" aria-controls="mobile-search" aria-expanded="false" >
		<svg viewBox="0 0 24 24" width="24" height="24" class="octicon octicon-search" role="presentation"><path d="M10.25 2a8.25 8.25 0 0 1 6.34 13.53l5.69 5.69a.749.749 0 0 1-.326 1.275.749.749 0 0 1-.734-.215l-5.69-5.69A8.25 8.25 0 1 1 10.25 2ZM3.5 10.25a6.75 6.75 0 1 0 13.5 0 6.75 6.75 0 0 0-13.5 0Z"></path></svg>
		<svg viewBox="2 2 20 20" width="24" height="24" class="octicon octicon-x" role="presentation"><path d="M5.72 5.72a.75.75 0 0 1 1.06 0L12 10.94l5.22-5.22a.749.749 0 0 1 1.275.326.749.749 0 0 1-.215.734L13.06 12l5.22 5.22a.749.749 0 0 1-.326 1.275.749.749 0 0 1-.734-.215L12 13.06l-5.22 5.22a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042L10.94 12 5.72 6.78a.75.75 0 0 1 0-1.06Z"></path></svg>
	</button>
	<button class="mobile-nav-focus-trap js-mobile-nav-focus-trap" data-target="#mobile-menu" data-focus-order="last"></button>
	<button id="mobile-menu-toggle"  aria-label="Toggle menu" class="ml-2 p-2 border-0 color-bg-transparent color-fg-default rounded-3 js-toggle" aria-controls="mobile-menu" aria-expanded="false" data-trap-focus="#header">
		<svg viewBox="0 0 16 16" width="24" height="24" class="octicon octicon-three-bars" role="presentation"><path d="M1 2.75A.75.75 0 0 1 1.75 2h12.5a.75.75 0 0 1 0 1.5H1.75A.75.75 0 0 1 1 2.75Zm0 5A.75.75 0 0 1 1.75 7h12.5a.75.75 0 0 1 0 1.5H1.75A.75.75 0 0 1 1 7.75ZM1.75 12h12.5a.75.75 0 0 1 0 1.5H1.75a.75.75 0 0 1 0-1.5Z"></path></svg>
		<svg viewBox="2 2 20 20" width="24" height="24" class="octicon octicon-x " role="presentation"><path d="M5.72 5.72a.75.75 0 0 1 1.06 0L12 10.94l5.22-5.22a.749.749 0 0 1 1.275.326.749.749 0 0 1-.215.734L13.06 12l5.22 5.22a.749.749 0 0 1-.326 1.275.749.749 0 0 1-.734-.215L12 13.06l-5.22 5.22a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042L10.94 12 5.72 6.78a.75.75 0 0 1 0-1.06Z"></path></svg>
	</button>
</div>
				</div>
			</div>
						
<form id="mobile-search" role="search" method="get" class="mobile-search" action="https://github.blog"  aria-hidden="true" aria-label="Search form">
	<div class="d-flex flex-1 p-3 color-bg-inset">
		<div class="d-flex flex-1 position-relative color-bg-transparent" data-color-mode="light" data-light-theme="light" data-dark-theme="dark" >
			<svg height="20" class="d-flex position-absolute z-3 octicon height-full ml-2 color-fg-subtle" aria-hidden="true" viewBox="0 0 16 16" version="1.1" width="20" role="img"><path fill-rule="evenodd" d="M11.5 7a4.499 4.499 0 11-8.998 0A4.499 4.499 0 0111.5 7zm-.82 4.74a6 6 0 111.06-1.06l3.04 3.04a.75.75 0 11-1.06 1.06l-3.04-3.04z"></path></svg>
			<input aria-label="Search the blog" type="search" class="pl-6 search-field form-control p-2 flex-1" placeholder="Search the blog…" value="" name="s" id="search-input">
		</div>

			<button		class="Button Button--size-medium Button--secondary ml-2"
				type="submit"									>
		<span class="Button__text">
		<span class="Text Text--200 Text--antialiased Text--weight-semibold Button--label Button--label-medium Button--label-secondary">
			Search		</span>
		</span>

			</button>
		</div>
</form>
			
<nav id="mobile-menu" class="mobile-menu position-relative overflow-y-auto flex-1 width-full rounded-top-3" aria-label="Navigation menu" aria-hidden="true" data-color-mode="light" data-light-theme="light" data-dark-theme="dark_dimmed">
	<div class="p-5">
				<ul id="menu-secondary-navigation" class="list-style-none"><li class="mb-5"><a href="https://github.blog/changelog/" aria-current="page" class="d-flex flex-items-center flex-justify-between lh-condensed-ultra text-bold color-fg-default">Changelog</a></li>
<li class="mb-5"><a href="https://docs.github.com/" class="d-flex flex-items-center flex-justify-between lh-condensed-ultra text-bold color-fg-default">Docs<svg viewBox="0 0 16 16" width="16" height="16" class="octicon octicon-link-external d-block color-fg-subtle" role="presentation"><path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.854-1h4.146a.25.25 0 0 1 .25.25v4.146a.25.25 0 0 1-.427.177L13.03 4.03 9.28 7.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.75-3.75-1.543-1.543A.25.25 0 0 1 10.604 1Z"></path></svg></a></li>
<li class="mb-5"><a href="https://github.com/customer-stories" class="d-flex flex-items-center flex-justify-between lh-condensed-ultra text-bold color-fg-default">Customer stories<svg viewBox="0 0 16 16" width="16" height="16" class="octicon octicon-link-external d-block color-fg-subtle" role="presentation"><path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.854-1h4.146a.25.25 0 0 1 .25.25v4.146a.25.25 0 0 1-.427.177L13.03 4.03 9.28 7.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.75-3.75-1.543-1.543A.25.25 0 0 1 10.604 1Z"></path></svg></a></li>
</ul>	<a		class="Button Button--size-medium Button--secondary Button--block my-3"
		href="https://githubuniverse.com/"				target="_blank"		data-analytics-click="Blog, click on button, text: Attend Universe; ref_location:top nav;"					>
		<span class="Button__text">
		<span class="Text Text--200 Text--antialiased Text--weight-semibold Button--label Button--label-medium Button--label-secondary">
			Attend GitHub Universe		</span>
		</span>

			</a>
		<a		class="Button Button--size-medium Button--primary Button--block"
		href="https://github.com/features/copilot/cli"				target="_blank"		data-analytics-click="Blog, click on button, text: Use Copilot for free; ref_location:top nav;"					>
		<span class="Button__text">
		<span class="Text Text--200 Text--antialiased Text--weight-semibold Button--label Button--label-medium Button--label-primary">
			Try GitHub Copilot CLI		</span>
		</span>

			</a>
		</div>
	<button class="mobile-nav-focus-trap js-mobile-nav-focus-trap" data-target="#mobile-menu-toggle" data-focus-order="target"></button>
</nav>
		</header>
	</div>
	<main id="start-of-content">
<div class="changelog-grid-top"></div>
	<div class="container-xl p-responsive-blog">
		<div class="BackLink-wrap">
			<a href="https://github.blog" class="BackLink LinkMono LinkMono--primary" data-analytics-click="Changelog, click on back link, text: Back to blog; ref_location:changelog landing header;">
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M7.78 12.53a.75.75 0 0 1-1.06 0L2.47 8.28a.75.75 0 0 1 0-1.06l4.25-4.25a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042L4.81 7h7.44a.75.75 0 0 1 0 1.5H4.81l2.97 2.97a.75.75 0 0 1 0 1.06Z"></path></svg>
				<span class="LinkUnderline">Back to blog</span>
			</a>
		</div>
	</div>
<div class="p-responsive-blog">
	<div class="ChangelogCentered pt-5 pt-md-10">
		<header class="ChangelogHeader">
	<h1 class="Heading--2"><span>Changelog</span></h1>
	<div class="SocialLinks">
		<copy-link>
			<a href="https://github.blog/changelog/feed/" 
				data-copy-label="Copied!"
				data-target="copy-link.element" 
				class="LinkMono LinkMono--muted LinkUnderline"
				data-analytics-click="Changelog, click to copy feed link, text: Copy RSS feed URL; ref_location:changelog header;"
			>
				Copy RSS feed URL			</a>
		</copy-link>
		<div class="SocialLinks-separator"></div>
		<a href="https://x.com/ghchangelog" 
			class="LinkMono LinkMono--muted LinkUnderline"
			data-analytics-click="Changelog, click on link, text: Follow @ghchangelog on X; ref_location:changelog header;"
		>
			Follow @ghchangelog on X		</a>
	</div>
</header>
<div class="ChangelogLine ChangelogLine--alt my-5"></div>	</div>
</div>

<div style="margin-bottom: 3rem;">
	<div class="ChangelogFilters-wrap p-responsive-blog">
		<changelog-filter-nav class="ChangelogFilters ChangelogFilters-scroll-start">
			<div class="ChangelogTypeFilters-wrap">
				<div class="ChangelogTypeFilters" data-target="changelog-filter-nav.type-filters">
					<div class="ChangelogTypeFilters-scroll-button-wrap ChangelogTypeFilters-scroll-button-wrap--start">
						<button type="button" aria-hidden="true" tabindex="-1" class="ChangelogTypeFilters-scroll-button" data-target="changelog-filter-nav.scroll-start">
							<span>
								<svg width="16" height="20" viewBox="0 0 16 20" fill="none" xmlns="http://www.w3.org/2000/svg">
									<path fill-rule="evenodd" clip-rule="evenodd" d="M9.78033 5.21967C9.48744 4.92678 9.01256 4.92678 8.71967 5.21967L4.4697 9.46967C4.1768 9.76256 4.1768 10.2374 4.4697 10.5303L8.71967 14.7803C9.01256 15.0732 9.48744 15.0732 9.78033 14.7803C10.0732 14.4874 10.0732 14.0126 9.78033 13.7197L6.06066 10L9.78033 6.28033C10.0732 5.98744 10.0732 5.51256 9.78033 5.21967Z" fill="currentColor"/>
								</svg>
							</span>
						</button>
					</div>

					<div class="ChangelogFilters-group" data-target="changelog-filter-nav.type-filters-inner">
												<type-filter id="filter-type-all" role="button" tabindex="0" aria-pressed="true" class="EditorialButton EditorialButton--alt EditorialButton--1" data-analytics-click="Changelog, click on type filter button, text: All; ref_location:changelog filters;">
							<img src="https://github.blog/wp-content/themes/github-2021-child/dist/img/icon-all.svg" width="16" height="16" aria-hidden="true" alt="">
							All						</type-filter>
													<type-filter id="filter-type-new-releases" role="button" tabindex="0" data-type="new-releases" aria-pressed="false"  class="EditorialButton EditorialButton--alt EditorialButton--2" data-analytics-click="Changelog, click on type filter button, text: New Releases; ref_location:changelog filters;">
								<img src="https://github.blog/wp-content/themes/github-2021-child/assets/img/icon-v3-new-releases.svg" width="16" height="16" aria-hidden="true" alt="">
								New Releases							</type-filter> 
													<type-filter id="filter-type-improvements" role="button" tabindex="0" data-type="improvements" aria-pressed="false"  class="EditorialButton EditorialButton--alt EditorialButton--3" data-analytics-click="Changelog, click on type filter button, text: Improvements; ref_location:changelog filters;">
								<img src="https://github.blog/wp-content/themes/github-2021-child/assets/img/icon-v3-improvements.svg" width="16" height="16" aria-hidden="true" alt="">
								Improvements							</type-filter> 
													<type-filter id="filter-type-deprecations" role="button" tabindex="0" data-type="deprecations" aria-pressed="false"  class="EditorialButton EditorialButton--alt EditorialButton--4" data-analytics-click="Changelog, click on type filter button, text: Retired; ref_location:changelog filters;">
								<img src="https://github.blog/wp-content/themes/github-2021-child/assets/img/icon-v3-deprecations.svg" width="16" height="16" aria-hidden="true" alt="">
								Retired							</type-filter> 
											</div>

					<div class="ChangelogTypeFilters-scroll-button-wrap ChangelogTypeFilters-scroll-button-wrap--end">
						<button type="button" aria-hidden="true" tabindex="-1" class="ChangelogTypeFilters-scroll-button" data-target="changelog-filter-nav.scroll-end">
							<span>	
								<svg width="16" height="20" viewBox="0 0 16 20" fill="none" xmlns="http://www.w3.org/2000/svg">
									<path fill-rule="evenodd" clip-rule="evenodd" d="M6.21967 5.21967C6.51256 4.92678 6.98744 4.92678 7.28033 5.21967L11.5303 9.46967C11.8232 9.76256 11.8232 10.2374 11.5303 10.5303L7.28033 14.7803C6.98744 15.0732 6.51256 15.0732 6.21967 14.7803C5.92678 14.4874 5.92678 14.0126 6.21967 13.7197L9.93934 10L6.21967 6.28033C5.92678 5.98744 5.92678 5.51256 6.21967 5.21967Z" fill="currentColor"/>
								</svg>
							</span>
						</button>
					</div>
				</div>
			</div>
							<filter-dialog-control role="button" tabindex="0" aria-haspopup="dialog" aria-controls="filter-dialog" class="EditorialButton EditorialButton--5 ChangelogAllFilters" data-target="filter-count.parent" data-analytics-click="Changelog, click on expand filters button, text: Filters; ref_location:changelog filters;">
					Filters <filter-count data-count="0" class="text-color-muted">(<span data-target="filter-count.value">0</span><span class="sr-only"> selected</span>)</filter-count>
					<img src="https://github.blog/wp-content/themes/github-2021-child/dist/img/icon-filter.svg" width="16" height="16" aria-hidden="true" alt="">
				</filter-dialog-control>
					</changelog-filter-nav>
	</div>

			<div class="p-responsive-blog">
			<filter-expose class="ChangelogFilters-expose ChangelogCentered" data-count="0">
				<template id="filter-expose-item" data-class="ChangelogFilters-expose-item Tag">
					<span data-target="filter-expose-item.title"></span>
					<button type="button" data-target="filter-expose-item.remove" aria-label="Remove filter" data-analytics-click="Changelog, click on remove filter button, text: Remove filter; ref_location:changelog expose filters;">
						<svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
							<path d="M2.21967 2.21967C2.51256 1.92678 2.98744 1.92678 3.28033 2.21967L5.99978 4.93912L8.7195 2.21984C9.0124 1.92694 9.48744 1.92678 9.78033 2.21967C10.0732 2.51256 10.0732 2.98748 9.78029 3.28037L7.06066 6L9.78018 8.71952C10.0731 9.01241 10.0732 9.48744 9.78033 9.78033C9.48744 10.0732 9.01252 10.0732 8.71963 9.78029L6 7.06066L3.28033 9.78033C2.98744 10.0732 2.51256 10.0732 2.21967 9.78033C1.92678 9.48744 1.92678 9.01256 2.21967 8.71967L4.93934 6L2.21967 3.28033C1.92678 2.98744 1.92678 2.51256 2.21967 2.21967Z" />
						</svg>
					</button>
				</template>

				
				<button type="button" data-target="filter-expose.clear" class="ChangelogFilters-expose-clear-all" aria-label="Clear all filters" data-analytics-click="Changelog, click on clear all filters button, text: Clear all; ref_location:changelog expose filters;">
					Clear all				</button>
			</filter-expose>
		</div>

		<dialog id="filter-dialog" aria-label="Filters" class="ChangelogDialog" style="width: 100%; max-width: 500px;">
			<div aria-label="">
				<div class="ChangelogDialog-heading">
					<h2 id="filter-dialog-title" class="Subheading Subheading--lg DialogTitle">
						Filters <filter-count data-count="0">(<span data-target="filter-count.value">0</span><span class="sr-only"> selected</span>)</filter-count>
					</h2>

					<button type="button" class="IconButton" data-action="close" aria-label="Close filter dialog">
						<svg aria-hidden="true" fill="currentColor" width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
							<path fill-rule="evenodd" clip-rule="evenodd" d="M18.2803 18.2803C18.5732 17.9874 18.5732 17.5126 18.2803 17.2197L13.0607 12L18.2803 6.78033C18.5732 6.48744 18.5732 6.01256 18.2803 5.71967C17.9874 5.42678 17.5126 5.42678 17.2197 5.71967L12 10.9393L6.78033 5.71967C6.48744 5.42678 6.01256 5.42678 5.71967 5.71967C5.42678 6.01256 5.42678 6.48744 5.71967 6.78033L10.9393 12L5.71967 17.2197C5.42678 17.5126 5.42678 17.9874 5.71967 18.2803C6.01256 18.5732 6.48744 18.5732 6.78033 18.2803L12 13.0607L17.2197 18.2803C17.5126 18.5732 17.9874 18.5732 18.2803 18.2803Z" />
						</svg>
					</button>
				</div>

				<filter-operator-toggle class="FilterDialog-operator-toggle" role="group" aria-label="Filter match type" data-operator="or">
					<span class="FilterDialog-operator-label">Match:</span>
					<label class="FilterDialog-operator-switch">
						<span class="FilterDialog-operator-switch-label" data-label="or">Any</span>
						<input type="checkbox" class="FilterDialog-operator-input"  aria-label="Toggle between Any and All">
						<span class="FilterDialog-operator-switch-slider"></span>
						<span class="FilterDialog-operator-switch-label" data-label="and">All</span>
					</label>
				</filter-operator-toggle>

				<filter-collector data-target="filters-clear.filter-collector" data-listen-event="changelog-filter" data-selector="input[type=checkbox]:checked" class="FilterDialog-body">
					<div class="FilterDialog-column">
						<div id="filter-dialog-subtitle" class="FilterDialog-column-title">Tags</div>

						<ul class="FilterDialog-filter-list" aria-labelledby="filter-dialog-title" aria-describedby="filter-dialog-subtitle">
							<li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:account-management"
				data-value="account-management"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Account management; ref_location:changelog-label-filter:label:account-management;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-account-management"
													value="account-management"
							>
		</div>
		Account management	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:actions"
				data-value="actions"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Actions; ref_location:changelog-label-filter:label:actions;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-actions"
													value="actions"
							>
		</div>
		Actions	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:application-security"
				data-value="application-security"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Application Security; ref_location:changelog-label-filter:label:application-security;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-application-security"
													value="application-security"
							>
		</div>
		Application Security	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:client-apps"
				data-value="client-apps"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Client apps; ref_location:changelog-label-filter:label:client-apps;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-client-apps"
													value="client-apps"
							>
		</div>
		Client apps	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:collaboration-tools"
				data-value="collaboration-tools"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Collaboration tools; ref_location:changelog-label-filter:label:collaboration-tools;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-collaboration-tools"
													value="collaboration-tools"
							>
		</div>
		Collaboration tools	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:community-engagement"
				data-value="community-engagement"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Community engagement; ref_location:changelog-label-filter:label:community-engagement;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-community-engagement"
													value="community-engagement"
							>
		</div>
		Community engagement	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:copilot"
				data-value="copilot"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Copilot; ref_location:changelog-label-filter:label:copilot;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-copilot"
													value="copilot"
							>
		</div>
		Copilot	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:ecosystem-and-accessibility"
				data-value="ecosystem-and-accessibility"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Ecosystem &amp; accessibility; ref_location:changelog-label-filter:label:ecosystem-and-accessibility;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-ecosystem-and-accessibility"
													value="ecosystem-and-accessibility"
							>
		</div>
		Ecosystem &amp; accessibility	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:enterprise-management-tools"
				data-value="enterprise-management-tools"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Enterprise management tools; ref_location:changelog-label-filter:label:enterprise-management-tools;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-enterprise-management-tools"
													value="enterprise-management-tools"
							>
		</div>
		Enterprise management tools	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:platform-governance"
				data-value="platform-governance"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Platform governance; ref_location:changelog-label-filter:label:platform-governance;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-platform-governance"
													value="platform-governance"
							>
		</div>
		Platform governance	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:projects-and-issues"
				data-value="projects-and-issues"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Projects &amp; Issues; ref_location:changelog-label-filter:label:projects-and-issues;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-projects-and-issues"
													value="projects-and-issues"
							>
		</div>
		Projects &amp; Issues	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:supply-chain-security"
				data-value="supply-chain-security"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Supply chain security; ref_location:changelog-label-filter:label:supply-chain-security;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-supply-chain-security"
													value="supply-chain-security"
							>
		</div>
		Supply chain security	</label>
</checkbox-state></li><li class="FilterDialog-filter-item">
<checkbox-state
			data-custom-event="changelog-label-filter:label:universe25"
				data-value="universe25"
	>
	<label class="CheckboxLabel CheckboxLabel--font-styled" data-analytics-click="Changelog, click on filter, text: Universe ‘25; ref_location:changelog-label-filter:label:universe25;">
		<div class="Checkbox-wrap">
			<svg viewBox="0 0 100 100" aria-hidden="true"><path fill="none" stroke="#00" stroke-width="13" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="10" d="M12.1 52.1l24.4 24.4 53-53"></path></svg>
			<input type="checkbox" class="Checkbox"
													id="filter-label-universe25"
													value="universe25"
							>
		</div>
		Universe ‘25	</label>
</checkbox-state></li>						</ul>
					</div>
				</filter-collector>

				<div class="FilterDialog-footer">
					<button type="button" data-action="clear" class="LinkButton LinkUnderline LinkUnderline--reverse" aria-label="Clear all filters">
						Clear all					</button>
						<button		class="Button Button--size-small Button--primary js-filter-close-button"
				type="button"						aria-label="Apply filters"								data-action="apply"
			>
		<span class="Button__text">
		<span class="Text Text--200 Text--antialiased Text--weight-semibold Button--label Button--label-small Button--label-primary">
			Apply		</span>
		</span>

			</button>
					</div>
			</div>
		</dialog>
	</div><div class="p-responsive-blog">
	<div class="ChangelogCentered">
		<changelog-months data-base-link="https://github.blog/changelog/">
			<div data-target="changelog-months.content">
				
<changelog-month class="ChangelogMonth" data-opened="true" data-loaded="true" data-origin-link="https://github.blog/changelog/2026/6/" data-month="6" data-month-slug="06-2026">
	<reactive-line-state class="ChangelogMonthHeading-wrap" data-state="resting">
		<h2 class="ChangelogMonthHeading Heading--4">
			<button type="button" data-href="https://github.blog/changelog/2026/6/" aria-expanded="true" data-target="changelog-month.toggle" data-targets="reactive-line-state.link"  class="ChangelogMonthHeading-action editorial-button-reset" aria-label="June 2026" data-analytics-click="Changelog, click to toggle visibility, text:June 2026; ref_location:changelog;">
				<span class="ChangelogMonth-name--full">June</span>
				<span class="ChangelogMonth-name--short">Jun</span>
				2026				<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
					<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
				</span>
			</button>

			<noscript>
				<a href="https://github.blog/changelog/2026/6/" aria-label="June 2026" class="ChangelogMonthHeading-action editorial-link-reset" data-analytics-click="Changelog, click to toggle visibility, text:June 2026; ref_location:changelog;">
					<span class="ChangelogMonth-name--full">June</span>
					<span class="ChangelogMonth-name--short">Jun</span>
					2026					<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
						<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
					</span>
				</a>
			</noscript>
		</h2>

		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>

	
	<div class="ChangelogMonthContent">
		<div class="ChangelogMonthcontent-anim">
			<div class="ChangelogMonthcontent-transform">
				<div data-target="changelog-month.content">
											<div class="ChangelogGroup">
							<article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96744">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-github-enterprise-server-3-21-is-now-generally-available" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96744">
					GitHub Enterprise Server 3.21 is now generally available				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=collaboration-tools" class="Tag" data-analytics-click="Changelog, click tag link, text: collaboration tools; ref_location:changelog post item tags;">collaboration tools</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96710">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-bot-created-pull-requests-can-run-workflows-if-approved" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96710">
					Bot-created pull requests can run workflows if approved				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=actions" class="Tag" data-analytics-click="Changelog, click tag link, text: actions; ref_location:changelog post item tags;">actions</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96735">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-ai-usage-report-updates" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96735">
					AI usage report updates				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96729">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-copilot-cli-configure-everything-from-one-place-with-settings" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96729">
					Copilot CLI: Configure everything from one place with /settings				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96725">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-new-runner-images-in-public-preview" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96725">
					New runner images in public preview				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=actions" class="Tag" data-analytics-click="Changelog, click tag link, text: actions; ref_location:changelog post item tags;">actions</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96714">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-github-agentic-workflows-is-now-in-public-preview" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96714">
					GitHub Agentic Workflows is now in public preview				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=actions" class="Tag" data-analytics-click="Changelog, click tag link, text: actions; ref_location:changelog post item tags;">actions</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96694">
				<time
	aria-label=" Jun 11." 
	class="Tag"datetime="2026-06-11">
	Jun.11</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-11-agentic-workflows-no-longer-need-a-personal-access-token" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96694">
					Agentic workflows no longer need a personal access token				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96678">
				<time
	aria-label=" Jun 10." 
	class="Tag"datetime="2026-06-10">
	Jun.10</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-10-list-view-and-create-discussions-in-github-cli" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96678">
					List, view, and create discussions in GitHub CLI				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96674">
				<time
	aria-label=" Jun 10." 
	class="Tag"datetime="2026-06-10">
	Jun.10</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-10-manage-sub-issues-types-and-dependencies-from-github-cli" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96674">
					Manage sub-issues, types, and dependencies from GitHub CLI				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96700">
				<time
	aria-label=" Jun 10." 
	class="Tag"datetime="2026-06-10">
	Jun.10</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-10-copilot-chat-now-sees-your-agent-sessions" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96700">
					Copilot Chat now sees your agent sessions				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96653">
				<time
	aria-label=" Jun 10." 
	class="Tag"datetime="2026-06-10">
	Jun.10</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-10-enterprises-can-now-create-up-to-500-cost-centers" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96653">
					Enterprises can now create up to 500 cost centers				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=account-management" class="Tag" data-analytics-click="Changelog, click tag link, text: account management; ref_location:changelog post item tags;">account management</a>
							<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96688">
				<time
	aria-label=" Jun 10." 
	class="Tag"datetime="2026-06-10">
	Jun.10</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-10-incremental-analysis-for-go-c-c-and-codeql-cli" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96688">
					Incremental analysis for Go, C/C++, and CodeQL CLI				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=application-security" class="Tag" data-analytics-click="Changelog, click tag link, text: application security; ref_location:changelog post item tags;">application security</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96684">
				<time
	aria-label=" Jun 10." 
	class="Tag"datetime="2026-06-10">
	Jun.10</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-10-dedicated-security-review-command-now-available-in-copilot-cli" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96684">
					Dedicated security review command now available in Copilot CLI				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=application-security" class="Tag" data-analytics-click="Changelog, click tag link, text: application security; ref_location:changelog post item tags;">application security</a>
							<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 2 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+2			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96681">
				<time
	aria-label=" Jun 9." 
	class="Tag"datetime="2026-06-09">
	Jun.09</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-09-dependabot-version-updates-now-support-the-deno-ecosystem" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96681">
					Dependabot version updates now support the Deno ecosystem				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=supply-chain-security" class="Tag" data-analytics-click="Changelog, click tag link, text: supply chain security; ref_location:changelog post item tags;">supply chain security</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96670">
				<time
	aria-label=" Jun 9." 
	class="Tag"datetime="2026-06-09">
	Jun.09</time>				<span class="Tag Tag--type-alt">Retired</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-09-upcoming-breaking-changes-for-npm-v12" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96670">
					Upcoming breaking changes for npm v12				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=supply-chain-security" class="Tag" data-analytics-click="Changelog, click tag link, text: supply chain security; ref_location:changelog post item tags;">supply chain security</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96666">
				<time
	aria-label=" Jun 9." 
	class="Tag"datetime="2026-06-09">
	Jun.09</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-09-claude-fable-5-is-generally-available-for-github-copilot" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96666">
					Claude Fable 5 is generally available for GitHub Copilot				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96660">
				<time
	aria-label=" Jun 9." 
	class="Tag"datetime="2026-06-09">
	Jun.09</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-09-periodic-code-scanning-of-inactive-repositories" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96660">
					Periodic code scanning of inactive repositories				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=application-security" class="Tag" data-analytics-click="Changelog, click tag link, text: application security; ref_location:changelog post item tags;">application security</a>
							<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96655">
				<time
	aria-label=" Jun 9." 
	class="Tag"datetime="2026-06-09">
	Jun.09</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-09-security-validation-for-third-party-coding-agents" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96655">
					Security validation for third-party coding agents				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=application-security" class="Tag" data-analytics-click="Changelog, click tag link, text: application security; ref_location:changelog post item tags;">application security</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96641">
				<time
	aria-label=" Jun 8." 
	class="Tag"datetime="2026-06-08">
	Jun.08</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-08-ip-allow-list-coverage-for-emu-namespaces-in-general-availability" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96641">
					IP allow list coverage for EMU namespaces in general availability				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96635">
				<time
	aria-label=" Jun 5." 
	class="Tag"datetime="2026-06-05">
	Jun.05</time>				<span class="Tag Tag--type-alt">Retired</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-05-gpt-5-2-and-gpt-5-2-codex-deprecated" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96635">
					GPT-5.2 and GPT-5.2-Codex deprecated				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96631">
				<time
	aria-label=" Jun 5." 
	class="Tag"datetime="2026-06-05">
	Jun.05</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-05-codeql-2-25-6-adds-swift-6-3-2-support-and-improves-c-coverage" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96631">
					CodeQL 2.25.6 adds Swift 6.3.2 support and improves C# coverage				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=application-security" class="Tag" data-analytics-click="Changelog, click tag link, text: application security; ref_location:changelog post item tags;">application security</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96628">
				<time
	aria-label=" Jun 5." 
	class="Tag"datetime="2026-06-05">
	Jun.05</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-05-enterprise-managed-plugins-in-vs-code-in-public-preview" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96628">
					Enterprise-managed plugins in VS Code in public preview				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
							<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 2 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+2			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96601">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-fix-with-copilot-for-failing-actions-now-in-pro-pro-and-max" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96601">
					Fix with Copilot for failing Actions now in Pro, Pro+, and Max				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96600">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-agent-tasks-rest-api-now-available-for-copilot-pro-pro-and-max" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96600">
					Agent tasks REST API now available for Copilot Pro, Pro+, and Max				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96598">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-budget-and-usage-management-apis-now-generally-available" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96598">
					Budget and usage management APIs now generally available				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=account-management" class="Tag" data-analytics-click="Changelog, click tag link, text: account management; ref_location:changelog post item tags;">account management</a>
							<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96597">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-api-access-to-billing-usage-reports-now-generally-available" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96597">
					API access to billing usage reports now generally available				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96595">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-larger-context-windows-and-configurable-reasoning-levels-for-github-copilot" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96595">
					Larger context windows and configurable reasoning levels for GitHub Copilot				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96510">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-github-copilot-in-visual-studio-may-update" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96510">
					GitHub Copilot in Visual Studio — May update				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96571">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-enterprise-teams-is-now-generally-available" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96571">
					Enterprise Teams is now generally available				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96584">
				<time
	aria-label=" Jun 4." 
	class="Tag"datetime="2026-06-04">
	Jun.04</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-04-copilot-chat-brings-richer-context-to-pull-requests" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96584">
					Copilot Chat brings richer context to pull requests				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96377">
				<time
	aria-label=" Jun 3." 
	class="Tag"datetime="2026-06-03">
	Jun.03</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-03-github-copilot-in-visual-studio-code-may-releases" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96377">
					GitHub Copilot in Visual Studio Code, May releases				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96559">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Retired</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-gpt-4-1-deprecated" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96559">
					GPT-4.1 deprecated				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96428">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-expanded-technical-preview-availability-for-the-github-copilot-app" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96428">
					Expanded technical preview availability for the GitHub Copilot app				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96443">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-copilot-sdk-is-now-generally-available" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96443">
					Copilot SDK is now generally available				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96422">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-copilot-cli-improved-ui-rubber-duck-prompt-scheduling-and-voice-input" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96422">
					Copilot CLI: Improved UI, rubber duck, prompt scheduling, and voice input				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=client-apps" class="Tag" data-analytics-click="Changelog, click tag link, text: client apps; ref_location:changelog post item tags;">client apps</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96469">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-cloud-and-local-sandboxes-for-github-copilot-now-in-public-preview" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96469">
					Cloud and local sandboxes for GitHub Copilot now in public preview				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=application-security" class="Tag" data-analytics-click="Changelog, click tag link, text: application security; ref_location:changelog post item tags;">application security</a>
							<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
							<a href="https://github.blog/changelog/2026/?label=platform-governance" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: platform governance; ref_location:changelog post item tags;">platform governance</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 2 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+2			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96440">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-shape-copilot-code-review-around-your-team" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96440">
					Shape Copilot code review around your team				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96414">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-github-copilot-code-review-for-azure-repos-is-now-in-technical-preview" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96414">
					GitHub Copilot code review for Azure Repos is now in technical preview				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96425">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-extend-github-with-agent-apps" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96425">
					Extend GitHub with agent apps				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96367">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Improvement</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-introducing-copilot-cli-and-agentic-capabilities-enhancements-in-jetbrains-ides" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96367">
					Introducing Copilot CLI and agentic capabilities enhancements in JetBrains IDEs				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96358">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-gemini-models-in-copilot-cli-cloud-agent-and-the-copilot-app" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96358">
					Gemini models in Copilot CLI, cloud agent, and the Copilot app				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96437">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-gain-insights-across-your-agent-sessions-with-chronicle" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96437">
					Gain insights across your agent sessions with /chronicle				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96434">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-schedule-and-automate-tasks-with-copilot-cloud-agent" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96434">
					Schedule and automate tasks with Copilot cloud agent				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96431">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-copilot-memory-supports-user-preferences-for-business-enterprise" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96431">
					Copilot Memory supports user preferences for Business, Enterprise				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
								<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
							<a href="https://github.blog/changelog/2026/?label=enterprise-management-tools" class="Tag" data-targets="unveil-tags.target" hidden data-analytics-click="Changelog, click tag link, text: enterprise management tools; ref_location:changelog post item tags;">enterprise management tools</a>
						<button type="button" data-target="unveil-tags.button" class="Tag" aria-expanded="false" aria-label="Show 1 more tags" data-analytics-click="Changelog, click on button to expand, text: Show more tags; ref_location:changelog post item tags;">
				<span aria-hidden="true" style="letter-spacing: -3.6px;">...</span>
				+1			</button>
			</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96419">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-mai-code-1-flash-is-now-available-for-github-copilot" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96419">
					MAI-Code-1-Flash is now available for GitHub Copilot				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96400">
				<time
	aria-label=" Jun 2." 
	class="Tag"datetime="2026-06-02">
	Jun.02</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-02-github-copilot-in-eclipse-byok-skills-and-chat-updates" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96400">
					GitHub Copilot in Eclipse: BYOK, skills, and chat updates				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96403">
				<time
	aria-label=" Jun 1." 
	class="Tag"datetime="2026-06-01">
	Jun.01</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-01-evaluation-models-in-auto-for-individual-plans" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96403">
					Evaluation models in auto for individual plans				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article><article>
	<reactive-line-state class="ChangelogItem" data-state="resting">
		<div class="ChangelogItem-content">
			<h3 class="ChangelogItem-content-meta" id="changelog-item-meta-96399">
				<time
	aria-label=" Jun 1." 
	class="Tag"datetime="2026-06-01">
	Jun.01</time>				<span class="Tag Tag--type-alt">Release</span>
							</h3>
			<div class="ChangelogItem-content-inner">
				<a data-targets="reactive-line-state.link" href="https://github.blog/changelog/2026-06-01-updates-to-github-copilot-billing-and-plans" class="ChangelogItem-title" aria-describedby="changelog-item-meta-96399">
					Updates to GitHub Copilot billing and plans				</a>
				<unveil-tags class="Tags ChangelogItem-tags">
	<div class="Tags-anim"  data-target="unveil-tags.animWrapper">
		<button type="button" data-target="unveil-tags.close" hidden class="Tag-close" aria-label="Close tag list" data-analytics-click="Changelog, click on button to close, text: Close tag list; ref_location:changelog post item tags;"></button>
									<a href="https://github.blog/changelog/2026/?label=copilot" class="Tag" data-analytics-click="Changelog, click tag link, text: copilot; ref_location:changelog post item tags;">copilot</a>
						</div>
</unveil-tags>
			</div>
		</div>
		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>
</article>						</div>
					
									</div>
			</div>
		</div>
	</div>
</changelog-month>
<changelog-month class="ChangelogMonth" data-opened="false" data-loaded="false" data-origin-link="https://github.blog/changelog/2026/5/" data-month="5" data-month-slug="05-2026">
	<reactive-line-state class="ChangelogMonthHeading-wrap" data-state="resting">
		<h2 class="ChangelogMonthHeading Heading--4">
			<button type="button" data-href="https://github.blog/changelog/2026/5/" aria-expanded="false" data-target="changelog-month.toggle" data-targets="reactive-line-state.link"  class="ChangelogMonthHeading-action editorial-button-reset" aria-label="May 2026" data-analytics-click="Changelog, click to toggle visibility, text:May 2026; ref_location:changelog;">
				<span class="ChangelogMonth-name--full">May</span>
				<span class="ChangelogMonth-name--short">May</span>
				2026				<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
					<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
				</span>
			</button>

			<noscript>
				<a href="https://github.blog/changelog/2026/5/" aria-label="May 2026" class="ChangelogMonthHeading-action editorial-link-reset" data-analytics-click="Changelog, click to toggle visibility, text:May 2026; ref_location:changelog;">
					<span class="ChangelogMonth-name--full">May</span>
					<span class="ChangelogMonth-name--short">May</span>
					2026					<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
						<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
					</span>
				</a>
			</noscript>
		</h2>

		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>

	
	<div class="ChangelogMonthContent">
		<div class="ChangelogMonthcontent-anim">
			<div class="ChangelogMonthcontent-transform">
				<div data-target="changelog-month.content">
					
											<div class="ChangelogMonthLoadingAnimation ChangelogGroup" style="margin-top: 2rem;">
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
						</div>
									</div>
			</div>
		</div>
	</div>
</changelog-month>
<changelog-month class="ChangelogMonth" data-opened="false" data-loaded="false" data-origin-link="https://github.blog/changelog/2026/4/" data-month="4" data-month-slug="04-2026">
	<reactive-line-state class="ChangelogMonthHeading-wrap" data-state="resting">
		<h2 class="ChangelogMonthHeading Heading--4">
			<button type="button" data-href="https://github.blog/changelog/2026/4/" aria-expanded="false" data-target="changelog-month.toggle" data-targets="reactive-line-state.link"  class="ChangelogMonthHeading-action editorial-button-reset" aria-label="April 2026" data-analytics-click="Changelog, click to toggle visibility, text:April 2026; ref_location:changelog;">
				<span class="ChangelogMonth-name--full">April</span>
				<span class="ChangelogMonth-name--short">Apr</span>
				2026				<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
					<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
				</span>
			</button>

			<noscript>
				<a href="https://github.blog/changelog/2026/4/" aria-label="April 2026" class="ChangelogMonthHeading-action editorial-link-reset" data-analytics-click="Changelog, click to toggle visibility, text:April 2026; ref_location:changelog;">
					<span class="ChangelogMonth-name--full">April</span>
					<span class="ChangelogMonth-name--short">Apr</span>
					2026					<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
						<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
					</span>
				</a>
			</noscript>
		</h2>

		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>

	
	<div class="ChangelogMonthContent">
		<div class="ChangelogMonthcontent-anim">
			<div class="ChangelogMonthcontent-transform">
				<div data-target="changelog-month.content">
					
											<div class="ChangelogMonthLoadingAnimation ChangelogGroup" style="margin-top: 2rem;">
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
						</div>
									</div>
			</div>
		</div>
	</div>
</changelog-month>
<changelog-month class="ChangelogMonth" data-opened="false" data-loaded="false" data-origin-link="https://github.blog/changelog/2026/3/" data-month="3" data-month-slug="03-2026">
	<reactive-line-state class="ChangelogMonthHeading-wrap" data-state="resting">
		<h2 class="ChangelogMonthHeading Heading--4">
			<button type="button" data-href="https://github.blog/changelog/2026/3/" aria-expanded="false" data-target="changelog-month.toggle" data-targets="reactive-line-state.link"  class="ChangelogMonthHeading-action editorial-button-reset" aria-label="March 2026" data-analytics-click="Changelog, click to toggle visibility, text:March 2026; ref_location:changelog;">
				<span class="ChangelogMonth-name--full">March</span>
				<span class="ChangelogMonth-name--short">Mar</span>
				2026				<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
					<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
				</span>
			</button>

			<noscript>
				<a href="https://github.blog/changelog/2026/3/" aria-label="March 2026" class="ChangelogMonthHeading-action editorial-link-reset" data-analytics-click="Changelog, click to toggle visibility, text:March 2026; ref_location:changelog;">
					<span class="ChangelogMonth-name--full">March</span>
					<span class="ChangelogMonth-name--short">Mar</span>
					2026					<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
						<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
					</span>
				</a>
			</noscript>
		</h2>

		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>

	
	<div class="ChangelogMonthContent">
		<div class="ChangelogMonthcontent-anim">
			<div class="ChangelogMonthcontent-transform">
				<div data-target="changelog-month.content">
					
											<div class="ChangelogMonthLoadingAnimation ChangelogGroup" style="margin-top: 2rem;">
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
						</div>
									</div>
			</div>
		</div>
	</div>
</changelog-month>
<changelog-month class="ChangelogMonth" data-opened="false" data-loaded="false" data-origin-link="https://github.blog/changelog/2026/2/" data-month="2" data-month-slug="02-2026">
	<reactive-line-state class="ChangelogMonthHeading-wrap" data-state="resting">
		<h2 class="ChangelogMonthHeading Heading--4">
			<button type="button" data-href="https://github.blog/changelog/2026/2/" aria-expanded="false" data-target="changelog-month.toggle" data-targets="reactive-line-state.link"  class="ChangelogMonthHeading-action editorial-button-reset" aria-label="February 2026" data-analytics-click="Changelog, click to toggle visibility, text:February 2026; ref_location:changelog;">
				<span class="ChangelogMonth-name--full">February</span>
				<span class="ChangelogMonth-name--short">Feb</span>
				2026				<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
					<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
				</span>
			</button>

			<noscript>
				<a href="https://github.blog/changelog/2026/2/" aria-label="February 2026" class="ChangelogMonthHeading-action editorial-link-reset" data-analytics-click="Changelog, click to toggle visibility, text:February 2026; ref_location:changelog;">
					<span class="ChangelogMonth-name--full">February</span>
					<span class="ChangelogMonth-name--short">Feb</span>
					2026					<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
						<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
					</span>
				</a>
			</noscript>
		</h2>

		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>

	
	<div class="ChangelogMonthContent">
		<div class="ChangelogMonthcontent-anim">
			<div class="ChangelogMonthcontent-transform">
				<div data-target="changelog-month.content">
					
											<div class="ChangelogMonthLoadingAnimation ChangelogGroup" style="margin-top: 2rem;">
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
						</div>
									</div>
			</div>
		</div>
	</div>
</changelog-month>
<changelog-month class="ChangelogMonth" data-opened="false" data-loaded="false" data-origin-link="https://github.blog/changelog/2026/1/" data-month="1" data-month-slug="01-2026">
	<reactive-line-state class="ChangelogMonthHeading-wrap" data-state="resting">
		<h2 class="ChangelogMonthHeading Heading--4">
			<button type="button" data-href="https://github.blog/changelog/2026/1/" aria-expanded="false" data-target="changelog-month.toggle" data-targets="reactive-line-state.link"  class="ChangelogMonthHeading-action editorial-button-reset" aria-label="January 2026" data-analytics-click="Changelog, click to toggle visibility, text:January 2026; ref_location:changelog;">
				<span class="ChangelogMonth-name--full">January</span>
				<span class="ChangelogMonth-name--short">Jan</span>
				2026				<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
					<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
				</span>
			</button>

			<noscript>
				<a href="https://github.blog/changelog/2026/1/" aria-label="January 2026" class="ChangelogMonthHeading-action editorial-link-reset" data-analytics-click="Changelog, click to toggle visibility, text:January 2026; ref_location:changelog;">
					<span class="ChangelogMonth-name--full">January</span>
					<span class="ChangelogMonth-name--short">Jan</span>
					2026					<span class="ChangelogMonthHeading-action ChangelogMonthHeading-toggleButton">
						<svg aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path d="M18.78 15.78a.749.749 0 0 1-1.06 0L12 10.061 6.28 15.78a.749.749 0 1 1-1.06-1.06l6.25-6.25a.749.749 0 0 1 1.06 0l6.25 6.25a.749.749 0 0 1 0 1.06Z"></path></svg>
					</span>
				</a>
			</noscript>
		</h2>

		<div class="ChangelogLine ChangelogLine--reactive"></div>
	</reactive-line-state>

	
	<div class="ChangelogMonthContent">
		<div class="ChangelogMonthcontent-anim">
			<div class="ChangelogMonthcontent-transform">
				<div data-target="changelog-month.content">
					
											<div class="ChangelogMonthLoadingAnimation ChangelogGroup" style="margin-top: 2rem;">
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
							<div class="ChangelogMonthLoadingAnimation-item"></div>
						</div>
									</div>
			</div>
		</div>
	</div>
</changelog-month>			</div>
			
<nav class= mt-6 mt-md-10" data-target="changelog-months.pagination" aria-labelledby="pagination-title">
	<h2 id="pagination-title" class="sr-only">Pagination</h2>
	<ul class="ChangelogPagination">
		<li>
			<span class="ChangelogPagination-item ChangelogPagination-prev" aria-label="Previous year">
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 12 12" width="12" height="12" fill="currentColor"><path d="M3.587 6.025c0 .2.1.4.2.5l3.3 3.3c.3.3.8.3 1.1 0 .3-.3.3-.8 0-1.1l-2.7-2.7 2.7-2.7c.3-.3.3-.8 0-1.1-.3-.3-.8-.3-1.1 0l-3.2 3.2c-.2.2-.3.4-.3.6Z"></path></svg>
				<span class="ChangelogPagination-item-label">Prev</span>
			</span>
		</li>
		<li class="ChangelogPagination-years--desktop">
			<ul class="ChangelogPagination-years">
															
						
						<li><a href="https://github.blog/changelog/2026/" class="ChangelogPagination-item ChangelogPagination-year" aria-current="page">2026</a></li>

													<li><a href="https://github.blog/changelog/2025/" class="ChangelogPagination-item ChangelogPagination-year">2025</a></li>
						
													<li><a href="https://github.blog/changelog/2024/" class="ChangelogPagination-item ChangelogPagination-year">2024</a></li>
						
						<li><span class="ChangelogPagination-item ChangelogPagination-ellipsis">...</span></li>

													<li><a href="https://github.blog/changelog/2018/" class="ChangelogPagination-item ChangelogPagination-year">2018</a></li>
																		</ul>
		</li>
		<li class="ChangelogPagination-years--mobile">
			<ul class="ChangelogPagination-years">
									
					
					
					<li><a href="https://github.blog/changelog/2026/" class="ChangelogPagination-item ChangelogPagination-year" aria-current="page">2026</a></li>

											<li><a href="https://github.blog/changelog/2025/" class="ChangelogPagination-item ChangelogPagination-year">2025</a></li>
					
											<li><span class="ChangelogPagination-item ChangelogPagination-ellipsis">...</span></li>
					
											<li><a href="https://github.blog/changelog/2018/" class="ChangelogPagination-item ChangelogPagination-year">2018</a></li>
												</ul>
		</li>
		<li>
			<a href="https://github.blog/changelog/2025/" class="ChangelogPagination-item ChangelogPagination-next" aria-label="Next year">
				<span class="ChangelogPagination-item-label">Next</span>
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 12 12" width="12" height="12" fill="currentColor"><path d="M4.7 10c-.2 0-.4-.1-.5-.2-.3-.3-.3-.8 0-1.1L6.9 6 4.2 3.3c-.3-.3-.3-.8 0-1.1.3-.3.8-.3 1.1 0l3.3 3.2c.3.3.3.8 0 1.1L5.3 9.7c-.2.2-.4.3-.6.3Z"></path></svg>
			</a>
		</li>
	</ul>
</nav>
		</changelog-months>
	</div>
</div>

<div class="changelog-grid-bottom"></div>
<div class="Newsletter editorial-typography bg-color-canvas-subtle">
	<div class="container-xl p-responsive-blog">
		<div class="Newsletter-content">
			<h2 class="Heading--4">Subscribe to our developer newsletter</h2>
			<p class="Paragraph">
				Discover tips, technical guides, and best practices in our biweekly newsletter just for devs.			</p>

			<form method="post" action="https://s88570519.t.eloqua.com/e/f2?elqFormName=copynewsletter-signup-form-637872624660309567&amp;elqSiteID=88570519" class="Newsletter-form">

				<form-field class="Newsletter-form-field">
					<label for="newsletter_emailAddress" class="ActiveInputLabel">
						Enter your email<sup>*</sup>
					</label>

					<input type="email" required id="newsletter_emailAddress" name="emailAddress" class="Input Newsletter-field" aria-required="true">
					<input type="hidden" name="classification" value="Practitioner">

					<button type="submit" class="Button Button--size-medium Button--primary Button--green" 
						data-analytics-click="
						Changelog, click on button, text: Subscribe; ref_location:newsletter signup form.						"
					>
						Subscribe					</button>
				</form-field>

				<p class="Newsletter-disclaimer editorial-content-block">
				By submitting, I agree to let GitHub and its affiliates use my information for personalized communications, targeted advertising, and campaign effectiveness. See the <a href="https://github.com/site/privacy" target="blank">GitHub Privacy Statement</a> for more details.				</p>
			</form>
		</div>
	</div>
</div>
<back-to-top>
	<a href="#start-of-content" data-target="back-to-top.link" class="ChangelogBackToTop" data-analytics-click="Changelog, click on link, text: Back to top; ref_location:changelog;">
		<div class="ChangelogBackToTop-labelWrapper">
			<span class="ChangelogBackToTop-label">
				Back to top			</span>
		</div>

		<div class="ChangelogBackToTop-iconWrapper">
			<svg class="ChangelogBackToTop-iconRest" aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16"><path d="M3.22 10.53a.749.749 0 0 1 0-1.06l4.25-4.25a.749.749 0 0 1 1.06 0l4.25 4.25a.749.749 0 1 1-1.06 1.06L8 6.811 4.28 10.53a.749.749 0 0 1-1.06 0Z"></path></svg>
			<svg class="ChangelogBackToTop-iconHover" aria-hidden="true" fill="currentColor" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16"><path d="M3.47 7.78a.75.75 0 0 1 0-1.06l4.25-4.25a.75.75 0 0 1 1.06 0l4.25 4.25a.751.751 0 0 1-.018 1.042.751.751 0 0 1-1.042.018L9 4.81v7.44a.75.75 0 0 1-1.5 0V4.81L4.53 7.78a.75.75 0 0 1-1.06 0Z"></path></svg>
		</div>
	</a>
</back-to-top>
</main>
<div data-color-mode="dark" data-light-theme="light" data-dark-theme="dark">
	<footer class="footer pt-6">
		<h2 class="sr-only">Site-wide Links</h2>
		
<div class="container-xl p-responsive-blog">
	<div class="d-flex flex-wrap py-5 mb-5">

		<div class="col-12 col-lg-4 mb-5">
			<a href="https://github.com/" data-ga-click="Resources, go to home, resources footer" class="color-fg-default" aria-label="Go to GitHub homepage">
				<svg height="30" class="octicon octicon-logo-github" viewBox="0 0 45 16" version="1.1" width="84" aria-hidden="true">
					<path fill-rule="evenodd" d="M18.53 12.03h-.02c.009 0 .015.01.024.011h.006l-.01-.01zm.004.011c-.093.001-.327.05-.574.05-.78 0-1.05-.36-1.05-.83V8.13h1.59c.09 0 .16-.08.16-.19v-1.7c0-.09-.08-.17-.16-.17h-1.59V3.96c0-.08-.05-.13-.14-.13h-2.16c-.09 0-.14.05-.14.13v2.17s-1.09.27-1.16.28c-.08.02-.13.09-.13.17v1.36c0 .11.08.19.17.19h1.11v3.28c0 2.44 1.7 2.69 2.86 2.69.53 0 1.17-.17 1.27-.22.06-.02.09-.09.09-.16v-1.5a.177.177 0 00-.146-.18zM42.23 9.84c0-1.81-.73-2.05-1.5-1.97-.6.04-1.08.34-1.08.34v3.52s.49.34 1.22.36c1.03.03 1.36-.34 1.36-2.25zm2.43-.16c0 3.43-1.11 4.41-3.05 4.41-1.64 0-2.52-.83-2.52-.83s-.04.46-.09.52c-.03.06-.08.08-.14.08h-1.48c-.1 0-.19-.08-.19-.17l.02-11.11c0-.09.08-.17.17-.17h2.13c.09 0 .17.08.17.17v3.77s.82-.53 2.02-.53l-.01-.02c1.2 0 2.97.45 2.97 3.88zm-8.72-3.61h-2.1c-.11 0-.17.08-.17.19v5.44s-.55.39-1.3.39-.97-.34-.97-1.09V6.25c0-.09-.08-.17-.17-.17h-2.14c-.09 0-.17.08-.17.17v5.11c0 2.2 1.23 2.75 2.92 2.75 1.39 0 2.52-.77 2.52-.77s.05.39.08.45c.02.05.09.09.16.09h1.34c.11 0 .17-.08.17-.17l.02-7.47c0-.09-.08-.17-.19-.17zm-23.7-.01h-2.13c-.09 0-.17.09-.17.2v7.34c0 .2.13.27.3.27h1.92c.2 0 .25-.09.25-.27V6.23c0-.09-.08-.17-.17-.17zm-1.05-3.38c-.77 0-1.38.61-1.38 1.38 0 .77.61 1.38 1.38 1.38.75 0 1.36-.61 1.36-1.38 0-.77-.61-1.38-1.36-1.38zm16.49-.25h-2.11c-.09 0-.17.08-.17.17v4.09h-3.31V2.6c0-.09-.08-.17-.17-.17h-2.13c-.09 0-.17.08-.17.17v11.11c0 .09.09.17.17.17h2.13c.09 0 .17-.08.17-.17V8.96h3.31l-.02 4.75c0 .09.08.17.17.17h2.13c.09 0 .17-.08.17-.17V2.6c0-.09-.08-.17-.17-.17zM8.81 7.35v5.74c0 .04-.01.11-.06.13 0 0-1.25.89-3.31.89-2.49 0-5.44-.78-5.44-5.92S2.58 1.99 5.1 2c2.18 0 3.06.49 3.2.58.04.05.06.09.06.14L7.94 4.5c0 .09-.09.2-.2.17-.36-.11-.9-.33-2.17-.33-1.47 0-3.05.42-3.05 3.73s1.5 3.7 2.58 3.7c.92 0 1.25-.11 1.25-.11v-2.3H4.88c-.11 0-.19-.08-.19-.17V7.35c0-.09.08-.17.19-.17h3.74c.11 0 .19.08.19.17z"></path>
				</svg>
			</a>
		</div>

		<nav aria-labelledby="product-menu" class="col-6 col-sm-3 col-lg-2 mb-6 mb-md-2 pr-3 pr-lg-0 pl-lg-4"><h3 id="product-menu" class="h5 mb-3 text-mono color-text-tertiary text-normal">Product</h3><ul class="list-style-none text-gray f5"><li class="lh-condensed mb-3"><a href="https://github.com/features" data-ga-click="Site Foundation Components, go to Features, site foundation components footer" class="Link--secondary">Features</a></li><li class="lh-condensed mb-3"><a href="https://github.com/security" data-ga-click="Site Foundation Components, go to Security, site foundation components footer" class="Link--secondary">Security</a></li><li class="lh-condensed mb-3"><a href="https://github.com/enterprise" data-ga-click="Site Foundation Components, go to Enterprise, site foundation components footer" class="Link--secondary">Enterprise</a></li><li class="lh-condensed mb-3"><a href="https://github.com/customer-stories?type=enterprise" data-ga-click="Site Foundation Components, go to Customer Stories, site foundation components footer" class="Link--secondary">Customer Stories</a></li><li class="lh-condensed mb-3"><a href="https://github.com/pricing" data-ga-click="Site Foundation Components, go to Pricing, site foundation components footer" class="Link--secondary">Pricing</a></li><li class="lh-condensed mb-3"><a href="https://resources.github.com/" data-ga-click="Site Foundation Components, go to Resources, site foundation components footer" class="Link--secondary">Resources</a></li></ul></nav><nav aria-labelledby="platform-menu" class="col-6 col-sm-3 col-lg-2 mb-6 mb-md-2 pr-3 pr-lg-0 pl-lg-4"><h3 id="platform-menu" class="h5 mb-3 text-mono color-text-tertiary text-normal">Platform</h3><ul class="list-style-none text-gray f5"><li class="lh-condensed mb-3"><a href="https://developer.github.com/" data-ga-click="Site Foundation Components, go to Developer API, site foundation components footer" class="Link--secondary">Developer API</a></li><li class="lh-condensed mb-3"><a href="https://partner.github.com/" data-ga-click="Site Foundation Components, go to Partners, site foundation components footer" class="Link--secondary">Partners</a></li><li class="lh-condensed mb-3"><a href="https://atom.io/" data-ga-click="Site Foundation Components, go to Atom, site foundation components footer" class="Link--secondary">Atom</a></li><li class="lh-condensed mb-3"><a href="https://www.electronjs.org/" data-ga-click="Site Foundation Components, go to Electron, site foundation components footer" class="Link--secondary">Electron</a></li><li class="lh-condensed mb-3"><a href="https://desktop.github.com/" data-ga-click="Site Foundation Components, go to GitHub Desktop, site foundation components footer" class="Link--secondary">GitHub Desktop</a></li></ul></nav><nav aria-labelledby="support-menu" class="col-6 col-sm-3 col-lg-2 mb-6 mb-md-2 pr-3 pr-lg-0 pl-lg-4"><h3 id="support-menu" class="h5 mb-3 text-mono color-text-tertiary text-normal">Support</h3><ul class="list-style-none text-gray f5"><li class="lh-condensed mb-3"><a href="https://docs.github.com/" data-ga-click="Site Foundation Components, go to Docs, site foundation components footer" class="Link--secondary">Docs</a></li><li class="lh-condensed mb-3"><a href="https://github.community/" data-ga-click="Site Foundation Components, go to Community Forum, site foundation components footer" class="Link--secondary">Community Forum</a></li><li class="lh-condensed mb-3"><a href="https://services.github.com/" data-ga-click="Site Foundation Components, go to Training, site foundation components footer" class="Link--secondary">Training</a></li><li class="lh-condensed mb-3"><a href="https://www.githubstatus.com/" data-ga-click="Site Foundation Components, go to Status, site foundation components footer" class="Link--secondary">Status</a></li><li class="lh-condensed mb-3"><a href="https://support.github.com/" data-ga-click="Site Foundation Components, go to Contact, site foundation components footer" class="Link--secondary">Contact</a></li></ul></nav><nav aria-labelledby="company-menu" class="col-6 col-sm-3 col-lg-2 mb-6 mb-md-2 pr-3 pr-lg-0 pl-lg-4"><h3 id="company-menu" class="h5 mb-3 text-mono color-text-tertiary text-normal">Company</h3><ul class="list-style-none text-gray f5"><li class="lh-condensed mb-3"><a href="https://github.com/about" data-ga-click="Site Foundation Components, go to About, site foundation components footer" class="Link--secondary">About</a></li><li class="lh-condensed mb-3"><a href="https://github.blog/" data-ga-click="Site Foundation Components, go to Blog, site foundation components footer" class="Link--secondary">Blog</a></li><li class="lh-condensed mb-3"><a href="https://github.com/about/careers" data-ga-click="Site Foundation Components, go to Careers, site foundation components footer" class="Link--secondary">Careers</a></li><li class="lh-condensed mb-3"><a href="https://github.com/about/press" data-ga-click="Site Foundation Components, go to Press, site foundation components footer" class="Link--secondary">Press</a></li><li class="lh-condensed mb-3"><a href="https://shop.github.com/" data-ga-click="Site Foundation Components, go to Shop, site foundation components footer" class="Link--secondary">Shop</a></li></ul></nav>
	</div>
</div>
		
<div class="color-bg-subtle">
	<div class="container-xl p-responsive-blog f6 py-4 d-sm-flex flex-justify-between flex-items-center">

	<ul class="list-style-none d-flex flex-wrap text-gray mb-3">
			<li class="mr-3">&copy; 2026 GitHub, Inc.</li>

			<li class="mr-3">
				<a href="https://docs.github.com/en/github/site-policy/github-terms-of-service" data-ga-click="Site Foundation Components, go to terms, site foundation components footer" class="Link--secondary">Terms</a>
			</li>

			<li class="mr-3">
				<a href="https://docs.github.com/en/github/site-policy/github-privacy-statement" data-ga-click="Site Foundation Components, go to privacy, site foundation components footer" class="Link--secondary">Privacy</a>
			</li>

				<li class="mr-3">
		<button type="button" class="btn-link Link--secondary" onClick="_ghcc.showPreferences()">Manage Cookies</button>
	</li>
	<li class="mr-3">
		<button type="button" class="btn-link Link--secondary" onClick="_ghcc.showPreferences()">Do not share my personal information</button>
	</li>
	
		</ul>

		<ul class="list-style-none d-flex flex-items-center mb-sm-0 lh-condensed-ultra">
			<li class="mr-3 flex-self-start focusable-svg">
				<a href="https://www.linkedin.com/company/github" data-ga-click="Blog, go to Linkedin, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 19 18" class="d-block" height="18">
						<title>LinkedIn icon</title>
						<path d="M3.94 2A2 2 0 1 1 2 0a2 2 0 0 1 1.94 2zM4 5.48H0V18h4zm6.32 0H6.34V18h3.94v-6.57c0-3.66 4.77-4 4.77 0V18H19v-7.93c0-6.17-7.06-5.94-8.72-2.91z" fill="currentColor"></path>
					</svg>

					<span class="sr-only">GitHub on LinkedIn</span>
				</a>
			</li>
			<li class="mr-3 focusable-svg">
				<a href="https://www.instagram.com/github/" data-ga-click="Blog, go to Instagram, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 169.063 169.063" class="d-block" height="18">
						<title>Instagram icon</title>
						<g>
							<path d="M122.406,0H46.654C20.929,0,0,20.93,0,46.655v75.752c0,25.726,20.929,46.655,46.654,46.655h75.752
								c25.727,0,46.656-20.93,46.656-46.655V46.655C169.063,20.93,148.133,0,122.406,0z M154.063,122.407
								c0,17.455-14.201,31.655-31.656,31.655H46.654C29.2,154.063,15,139.862,15,122.407V46.655C15,29.201,29.2,15,46.654,15h75.752
								c17.455,0,31.656,14.201,31.656,31.655V122.407z" fill="currentColor"/>
							<path d="M84.531,40.97c-24.021,0-43.563,19.542-43.563,43.563c0,24.02,19.542,43.561,43.563,43.561s43.563-19.541,43.563-43.561
								C128.094,60.512,108.552,40.97,84.531,40.97z M84.531,113.093c-15.749,0-28.563-12.812-28.563-28.561
								c0-15.75,12.813-28.563,28.563-28.563s28.563,12.813,28.563,28.563C113.094,100.281,100.28,113.093,84.531,113.093z" fill="currentColor"/>
							<path d="M129.921,28.251c-2.89,0-5.729,1.17-7.77,3.22c-2.051,2.04-3.23,4.88-3.23,7.78c0,2.891,1.18,5.73,3.23,7.78
								c2.04,2.04,4.88,3.22,7.77,3.22c2.9,0,5.73-1.18,7.78-3.22c2.05-2.05,3.22-4.89,3.22-7.78c0-2.9-1.17-5.74-3.22-7.78
								C135.661,29.421,132.821,28.251,129.921,28.251z" fill="currentColor"/>
						</g>
					</svg>

					<span class="sr-only">GitHub on Instagram</span>
				</a>
			</li>
			<li class="mr-3 focusable-svg">
				<a href="https://www.youtube.com/github" data-ga-click="Blog, go to YouTube, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 19.17 13.6" class="d-block" height="16">
						<title>YouTube icon</title>
						<path d="M18.77 2.13A2.4 2.4 0 0 0 17.09.42C15.59 0 9.58 0 9.58 0a57.55 57.55 0 0 0-7.5.4A2.49 2.49 0 0 0 .39 2.13 26.27 26.27 0 0 0 0 6.8a26.15 26.15 0 0 0 .39 4.67 2.43 2.43 0 0 0 1.69 1.71c1.52.42 7.5.42 7.5.42a57.69 57.69 0 0 0 7.51-.4 2.4 2.4 0 0 0 1.68-1.71 25.63 25.63 0 0 0 .4-4.67 24 24 0 0 0-.4-4.69zM7.67 9.71V3.89l5 2.91z" fill="currentColor"></path>
					</svg>

					<span class="sr-only">GitHub on YouTube</span>
				</a>
			</li>
			<li class="mr-3 focusable-svg">
				<a href="https://twitter.com/github" data-ga-click="Blog, go to X, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 1227" class="d-block" height="18">
						<title>X icon</title>
						<path xmlns="http://www.w3.org/2000/svg" d="M714.163 519.284 1160.89 0h-105.86L667.137 450.887 357.328 0H0l468.492 681.821L0 1226.37h105.866l409.625-476.152 327.181 476.152H1200L714.137 519.284h.026ZM569.165 687.828l-47.468-67.894-377.686-540.24h162.604l304.797 435.991 47.468 67.894 396.2 566.721H892.476L569.165 687.854v-.026Z" fill="currentColor"></path>
					</svg>

					<span class="sr-only">GitHub on X</span>
				</a>
			</li>
			<li class="mr-3 flex-self-start focusable-svg">
				<a href="https://www.tiktok.com/@github" data-ga-click="Blog, go to TikTok, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" class="d-block" height="18">
						<title>TikTok icon</title>
						<path d="M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.09 1.75 4.17 1.12 1.11 2.7 1.62 4.24 1.79v4.03c-1.44-.05-2.89-.35-4.2-.97-.57-.26-1.1-.59-1.62-.93-.01 2.92.01 5.84-.02 8.75-.08 1.4-.54 2.79-1.35 3.94-1.31 1.92-3.58 3.17-5.91 3.21-1.43.08-2.86-.31-4.08-1.03-2.02-1.19-3.44-3.37-3.65-5.71-.02-.5-.03-1-.01-1.49.18-1.9 1.12-3.72 2.58-4.96 1.66-1.44 3.98-2.13 6.15-1.72.02 1.48-.04 2.96-.04 4.44-.99-.32-2.15-.23-3.02.37-.63.41-1.11 1.04-1.36 1.75-.21.51-.15 1.07-.14 1.61.24 1.64 1.82 3.02 3.5 2.87 1.12-.01 2.19-.66 2.77-1.61.19-.33.4-.67.41-1.06.1-1.79.06-3.57.07-5.36.01-4.03-.01-8.05.02-12.07z" fill="currentColor"/>
					</svg>

					<span class="sr-only">GitHub on TikTok</span>
				</a>
			</li>
			<li class="mr-3 flex-self-start focusable-svg">
				<a href="https://www.twitch.tv/github" data-ga-click="Blog, go to Twitch, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" class="d-block" height="18">
						<title>Twitch icon</title>
						<path d="M11.571 4.714h1.715v5.143H11.57zm4.715 0H18v5.143h-1.714zM6 0L1.714 4.286v15.428h5.143V24l4.286-4.286h3.428L22.286 12V0zm14.571 11.143l-3.428 3.428h-3.429l-3 3v-3H6.857V1.714h13.714Z" fill="currentColor"/>
					</svg>
					<span class="sr-only">GitHub on Twitch</span>
				</a>
			</li>
			<li class="focusable-svg">
				<a href="https://github.com/github" data-ga-click="Blog, go to github's org, resources footer" style="color: #959da5;">
					<svg focusable="false" tabindex="-1" aria-hidden="true" height="20" class="octicon octicon-mark-github d-block" alt="" viewBox="0 0 98 96" fill="none" version="1.1" width="20" aria-hidden="true"><g clip-path="url(#a)">
						<title>GitHub icon</title>
						<path fill-rule="evenodd" d="M41.44 69.385C28.807 67.853 19.906 58.762 19.906 46.99c0-4.785 1.723-9.953 4.594-13.398-1.244-3.158-1.053-9.858.383-12.633 3.828-.479 8.996 1.531 12.058 4.307 3.637-1.149 7.465-1.723 12.155-1.723 4.69 0 8.517.574 11.963 1.627 2.966-2.68 8.23-4.69 12.058-4.211 1.34 2.584 1.531 9.283.287 12.537 3.063 3.637 4.69 8.518 4.69 13.494 0 11.772-8.9 20.672-21.725 22.3 3.254 2.104 5.455 6.698 5.455 11.962v9.953c0 2.871 2.393 4.498 5.264 3.35C84.41 87.95 98 70.629 98 49.19 98 22.107 75.988 0 48.904 0 21.82 0 0 22.107 0 49.191c0 21.246 13.494 38.856 31.678 45.46 2.584.956 5.072-.766 5.072-3.35v-7.657c-1.34.575-3.063.958-4.594.958-6.316 0-10.049-3.446-12.728-9.858-1.053-2.584-2.201-4.115-4.403-4.402-1.148-.096-1.53-.574-1.53-1.149 0-1.148 1.913-2.01 3.827-2.01 2.776 0 5.168 1.723 7.657 5.264 1.914 2.776 3.923 4.02 6.316 4.02 2.392 0 3.924-.861 6.125-3.063 1.627-1.627 2.871-3.062 4.02-4.02z"/></g><defs><clipPath id="a"><path d="M0 0h98v96H0z"/></clipPath></defs>
					</svg>
					<span class="sr-only">GitHub’s organization on GitHub</span>
				</a>
			</li>
		</ul>



	</div>
</div>
	</footer>
</div>
<script type="speculationrules">
{"prefetch":[{"source":"document","where":{"and":[{"href_matches":"/*"},{"not":{"href_matches":["/wp-*.php","/wp-admin/*","/wp-content/uploads/*","/wp-content/*","/wp-content/plugins/*","/wp-content/themes/github-2021-child/*","/wp-content/themes/github-2021/*","/*\\?(.+)"]}},{"not":{"selector_matches":"a[rel~=\"nofollow\"]"}},{"not":{"selector_matches":".no-prefetch, .no-prefetch a"}}]},"eagerness":"conservative"}]}
</script>
<div id="ghcc" style="position: sticky; bottom: 0; z-index: 99999;"></div><script src="https://ghcc.githubassets.com/ghcc.min.js" id="github_cookie_consent-js"></script>
<script src="https://js.monitor.azure.com/scripts/c/ms.analytics-web-4.js" id="github_microsoft_analytics-js"></script>
<script type="text/javascript" src="https://github.blog/_static/??-eJx9jTESAiEMRS8kG5aGtXA8CwIDYSHsGBivb7RQK9u891/gcSjfaUQacNSZkBhSllPfMb4IC1GOghp353ekBAF5QGH44yyFT/BTHjm2KGEced6U0WZVPmMN31Z2lGLtnyWSrzPIRuBbyvKNl4YkxrVdVrvps9HWbuUJzdNGdA==" ></script><script src="https://github.blog/wp-includes/js/dist/i18n.min.js?ver=c26c3dc7bed366793375" id="wp-i18n-js"></script>
<script id="wp-i18n-js-after">
wp.i18n.setLocaleData( { 'text direction\u0004ltr': [ 'ltr' ] } );
//# sourceURL=wp-i18n-js-after
</script>
<script type="text/javascript" src="https://github.blog/wp-includes/js/dist/url.min.js?m=1778775779g" ></script><script src="https://github.blog/wp-includes/js/dist/api-fetch.min.js?ver=3a4d9af2b423048b0dee" id="wp-api-fetch-js"></script>
<script id="wp-api-fetch-js-after">
wp.apiFetch.use( wp.apiFetch.createRootURLMiddleware( "https://github.blog/wp-json/" ) );
wp.apiFetch.nonceMiddleware = wp.apiFetch.createNonceMiddleware( "e75eae4b16" );
wp.apiFetch.use( wp.apiFetch.nonceMiddleware );
wp.apiFetch.use( wp.apiFetch.mediaUploadMiddleware );
wp.apiFetch.nonceEndpoint = "https://github.blog/wp-admin/admin-ajax.php?action=rest-nonce";
//# sourceURL=wp-api-fetch-js-after
</script>
<script type="text/javascript" src="https://github.blog/_static/??/wp-content/themes/github-2021/dist/js/site-script.js,/wp-content/plugins/page-links-to/dist/new-tab.js?m=1780920778j" ></script><script integrity="sha256-kAnFXX7lCXF9K2o4g5q1lKyk167yRq6C4TiXWtbgvQw=" crossorigin="anonymous" src="https://analytics.githubassets.com/v1.1.0/hydro-marketing.min.js?ver=6.9.4" id="hydro-analytics-js"></script>
<script id="jetpack-stats-js-before">
_stq = window._stq || [];
_stq.push([ "view", {"v":"ext","blog":"153214340","post":"0","tz":"-7","srv":"github.blog","arch_results":"20","hp":"vip","j":"1:15.7"} ]);
_stq.push([ "clickTrackerInit", "153214340", "0" ]);
//# sourceURL=jetpack-stats-js-before
</script>
<script src="https://stats.wp.com/e-202624.js" id="jetpack-stats-js" defer data-wp-strategy="defer"></script>
<script id="wp-emoji-settings" type="application/json">
{"baseUrl":"https://s.w.org/images/core/emoji/17.0.2/72x72/","ext":".png","svgUrl":"https://s.w.org/images/core/emoji/17.0.2/svg/","svgExt":".svg","source":{"concatemoji":"https://github.blog/wp-includes/js/wp-emoji-release.min.js?ver=6.9.4"}}
</script>
<script type="module">
/*! This file is auto-generated */
const a=JSON.parse(document.getElementById("wp-emoji-settings").textContent),o=(window._wpemojiSettings=a,"wpEmojiSettingsSupports"),s=["flag","emoji"];function i(e){try{var t={supportTests:e,timestamp:(new Date).valueOf()};sessionStorage.setItem(o,JSON.stringify(t))}catch(e){}}function c(e,t,n){e.clearRect(0,0,e.canvas.width,e.canvas.height),e.fillText(t,0,0);t=new Uint32Array(e.getImageData(0,0,e.canvas.width,e.canvas.height).data);e.clearRect(0,0,e.canvas.width,e.canvas.height),e.fillText(n,0,0);const a=new Uint32Array(e.getImageData(0,0,e.canvas.width,e.canvas.height).data);return t.every((e,t)=>e===a[t])}function p(e,t){e.clearRect(0,0,e.canvas.width,e.canvas.height),e.fillText(t,0,0);var n=e.getImageData(16,16,1,1);for(let e=0;e<n.data.length;e++)if(0!==n.data[e])return!1;return!0}function u(e,t,n,a){switch(t){case"flag":return n(e,"\ud83c\udff3\ufe0f\u200d\u26a7\ufe0f","\ud83c\udff3\ufe0f\u200b\u26a7\ufe0f")?!1:!n(e,"\ud83c\udde8\ud83c\uddf6","\ud83c\udde8\u200b\ud83c\uddf6")&&!n(e,"\ud83c\udff4\udb40\udc67\udb40\udc62\udb40\udc65\udb40\udc6e\udb40\udc67\udb40\udc7f","\ud83c\udff4\u200b\udb40\udc67\u200b\udb40\udc62\u200b\udb40\udc65\u200b\udb40\udc6e\u200b\udb40\udc67\u200b\udb40\udc7f");case"emoji":return!a(e,"\ud83e\u1fac8")}return!1}function f(e,t,n,a){let r;const o=(r="undefined"!=typeof WorkerGlobalScope&&self instanceof WorkerGlobalScope?new OffscreenCanvas(300,150):document.createElement("canvas")).getContext("2d",{willReadFrequently:!0}),s=(o.textBaseline="top",o.font="600 32px Arial",{});return e.forEach(e=>{s[e]=t(o,e,n,a)}),s}function r(e){var t=document.createElement("script");t.src=e,t.defer=!0,document.head.appendChild(t)}a.supports={everything:!0,everythingExceptFlag:!0},new Promise(t=>{let n=function(){try{var e=JSON.parse(sessionStorage.getItem(o));if("object"==typeof e&&"number"==typeof e.timestamp&&(new Date).valueOf()<e.timestamp+604800&&"object"==typeof e.supportTests)return e.supportTests}catch(e){}return null}();if(!n){if("undefined"!=typeof Worker&&"undefined"!=typeof OffscreenCanvas&&"undefined"!=typeof URL&&URL.createObjectURL&&"undefined"!=typeof Blob)try{var e="postMessage("+f.toString()+"("+[JSON.stringify(s),u.toString(),c.toString(),p.toString()].join(",")+"));",a=new Blob([e],{type:"text/javascript"});const r=new Worker(URL.createObjectURL(a),{name:"wpTestEmojiSupports"});return void(r.onmessage=e=>{i(n=e.data),r.terminate(),t(n)})}catch(e){}i(n=f(s,u,c,p))}t(n)}).then(e=>{for(const n in e)a.supports[n]=e[n],a.supports.everything=a.supports.everything&&a.supports[n],"flag"!==n&&(a.supports.everythingExceptFlag=a.supports.everythingExceptFlag&&a.supports[n]);var t;a.supports.everythingExceptFlag=a.supports.everythingExceptFlag&&!a.supports.flag,a.supports.everything||((t=a.source||{}).concatemoji?r(t.concatemoji):t.wpemoji&&t.twemoji&&(r(t.twemoji),r(t.wpemoji)))});
//# sourceURL=https://github.blog/wp-includes/js/wp-emoji-loader.min.js
</script>
</body>
</html>
