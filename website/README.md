# Sunny website

Static marketing and policy site for `https://sunny.adityaps.work`.

## Preview locally

```bash
python3 -m http.server 4173 --directory website
```

Open `http://localhost:4173`.

## Deploy

Deploy the `website/` directory as the site root on Cloudflare Pages, GitHub
Pages, Netlify, Vercel static hosting, or any conventional web server. The site
has no build step and no runtime dependencies.

For Cloudflare Pages, use:

- Build command: leave empty
- Build output directory: `website`
- Custom domain: `sunny.adityaps.work`

For a conventional DNS setup, point `sunny.adityaps.work` to the hosting
provider using the CNAME or A/AAAA value it supplies. Do not point it at the GPU
inference server unless that machine is intentionally configured to host the
site with independent uptime and security controls.

## Before publishing

Confirm that `hello@adityaps.work` and `privacy@adityaps.work` are monitored
mailboxes or aliases. Replace them throughout the three HTML files if different
addresses should be used. The Play Console privacy-policy URL is:

```text
https://sunny.adityaps.work/privacy/
```
