# Sign-in site

Static files that must be served from the **root** of `https://adderloops.github.io/`
(repository `Adderloops/Adderloops.github.io`, GitHub Pages, branch `main`, folder `/`):

- `.well-known/assetlinks.json` — tells Android this app may open the sign-in callback link
- `oauth/callback/index.html` — page Twitch redirects to; the app takes over the link
- `.nojekyll` — makes GitHub Pages publish the hidden `.well-known` folder

The Twitch application must list `https://adderloops.github.io/oauth/callback` as an OAuth Redirect URL.
If the signing key or package name changes, update the fingerprint / package in `assetlinks.json`.
