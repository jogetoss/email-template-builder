# Description

Email Template Builder adds a builder to Joget DX 9 for designing reusable email templates. It's built on [MJML](https://mjml.io), compiled in Java inside the plugin by [mjml4j](https://github.com/digitalfondue/mjml4j), so emails come out as Outlook-safe HTML that adapts to phones. There's no Node and no external calls.

Features:
- A visual builder: drag blocks onto the canvas, click text to edit it, and switch between desktop and mobile views.
- 20 blocks:
  - content: Text, Button, Image, Navbar, Custom HTML, Divider, Spacer
  - layout: Row, Column, Hero, Outside the card
  - data: Datalist Table, and Repeat (a card per datalist row)
  - components: Steps, Progress bars, Facts list, Timeline, Stat tiles, Badge, Callout
- 27 starter templates (approval request, daily digest, password reset, receipt, newsletter and more) and 24 ready-made components.
- Merge fields such as `#form.request.title#` anywhere in the email, and template variables (`{var.NAME}`) for the email's inputs. Merge values are HTML-escaped, so form input can't add links or scripts.
- **Preview**, **Send test**, and a size meter against Gmail's clipping limit.
- The **Send Templated Email** process tool, which sends HTML and a plain-text version and embeds App Resource images. It uses the same SMTP, attachment and calendar invite settings as the Email Tool.
- An `#emailTemplate.ID#` hash variable for the stock Email Tool.

# Getting Started

1. In an app, create an **Email Template**. It gets an ID such as `ET-001`.
2. In the builder, click **Templates** to start from a starter, or add blocks from the palette. Set the subject in **Settings**.
3. Check the result with **Preview** and **Send test**. **Test data** fills merge fields from a form record you choose.
4. Send it from a process:
   - **Send Templated Email** (recommended): pick the template, map its template variables, and set the recipients as in the Email Tool.
   - **Email Tool**: set the message to `#emailTemplate.ET-001?noescape#` with **HTML** ticked, and the subject to `#emailTemplate.ET-001.subject#`. `?noescape` is required, or Joget strips the email's layout. This route sends HTML only, and `{var.…}` template variables come out empty.

# Getting Help

JogetOSS is a community-led team for open source software related to the [Joget](https://www.joget.org) no-code/low-code application platform.
Projects under JogetOSS are community-driven and community-supported.
To obtain support, ask questions, get answers and help others, please participate in the [Community Q&A](https://answers.joget.org/).

# Contributing

This project welcomes contributions and suggestions, please open an issue or create a pull request.

Please note that all interactions fall under our [Code of Conduct](https://github.com/jogetoss/repo-template/blob/main/CODE_OF_CONDUCT.md).

# Licensing

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

NOTE: This software may depend on other packages that may be licensed under different open source licenses. The plugin jar embeds:
- [mjml4j](https://github.com/digitalfondue/mjml4j) 1.2.2, MIT License
- [jfiveparse](https://github.com/digitalfondue/jfiveparse) 2.0.1, Apache License 2.0
