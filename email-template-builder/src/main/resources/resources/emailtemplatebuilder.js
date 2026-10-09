/*
 * Email Template Builder canvas, on the DX 9 CustomBuilder.Builder iframe canvas.
 * Every block is compiled on the server by the same mjml4j renderer as the sent email.
 * Text blocks use the framework's built-in inline editor (builderTemplate.inlineEditor).
 */
EmailTemplateBuilder = {

    rootClass: "org.joget.etb.EmailTemplateBuilder",

    serviceUrl: function(action) {
        return CustomBuilder.contextPath + '/web/json/app/' + CustomBuilder.appId + '/' + CustomBuilder.appVersion
                + '/plugin/' + EmailTemplateBuilder.rootClass + '/service?action=' + action;
    },

    initBuilder: function (callback) {
        EmailTemplateBuilder.registerColorField();
        EmailTemplateBuilder.registerRowFieldsField();

        CustomBuilder.Builder.init({
            "enableViewport": true,
            callbacks: {
                "renderElement": "EmailTemplateBuilder.renderBlock"
            }
        }, function () {
            CustomBuilder.Builder.setHead('<style data-etb-style>'
                // the canvas iframe body has 25px side padding; on a phone the email is edge to edge, so without
                // this the canvas is 50px narrower than the real email and text wraps earlier than in Preview
                + 'body{padding-left:0 !important;padding-right:0 !important;}'
                + '.etb-email{max-width:600px;margin:0 auto;}'
                + '.etb-email:not(:has([data-cbuilder-classname])){min-height:240px;margin:24px auto;background:var(--etb-card-bg);border-radius:var(--etb-card-radius);box-shadow:var(--etb-card-shadow);}'
                // the card, per segment: a run of blocks between "Outside the card" blocks (like the email's card wrappers)
                + '.etb-email > .etb-seg{background-color:var(--etb-card-bg);box-shadow:var(--etb-card-shadow);clip-path:inset(0 -24px);}'
                + '.etb-email > .etb-seg-first{border-top-left-radius:var(--etb-card-radius);border-top-right-radius:var(--etb-card-radius);overflow:hidden;clip-path:inset(-24px -24px 0 -24px);}'
                + '.etb-email > .etb-seg-last{border-bottom-left-radius:var(--etb-card-radius);border-bottom-right-radius:var(--etb-card-radius);overflow:hidden;clip-path:inset(0 -24px -24px -24px);}'
                + '.etb-email > .etb-seg-first.etb-seg-last{clip-path:none;}'
                + '.etb-email > .etb-page-first{margin-top:24px;}'
                + '.etb-email > .etb-page-last{margin-bottom:24px;}'
                + '.etb-outside{position:relative;box-sizing:border-box;}'
                + '.etb-outside::before{content:"Outside the card";position:absolute;top:2px;left:4px;font:10px Arial,sans-serif;color:#9aa5b1;}'
                + '.etb-outside:not(:has(> [data-cbuilder-classname])){outline:1px dashed #b8c7b0;outline-offset:-4px;}'
                // Row/Column: client-side approximation of mj-section/mj-column; the blocks inside are compiled by the server
                + '.etb-row{display:flex;box-sizing:border-box;}'
                + '.etb-repeat{position:relative;padding-top:22px !important;}'
                + '.etb-repeat::before{content:attr(data-etb-label);position:absolute;top:2px;left:calc(var(--etb-pad-l, 24px) + 8px);font:11px Arial,sans-serif;color:#6e9f4b;letter-spacing:.02em;}'
                + '.etb-repeat-card{box-sizing:border-box;}'
                + '.etb-repeat-fill{position:absolute;top:0;right:var(--etb-pad-r, 24px);border:1px solid #6e9f4b;background:#fff;color:#4d7a32;border-radius:12px;padding:1px 10px;font:11px Arial,sans-serif;cursor:pointer;z-index:2;}'
                + '.etb-repeat-fill:hover{background:#6e9f4b;color:#fff;}'
                // real rows under the card: clearly not part of the design (dashed, faded header), never a drop target
                + '.etb-repeat-live{margin-top:10px;border-top:1px dashed #b8c7b0;padding-top:6px;}'
                + '.etb-repeat-live.loading{opacity:.55;}'
                + '.etb-repeat-live-head{font:11px/1.4 Arial,sans-serif;color:#6e9f4b;padding:0 8px 6px 8px;}'
                + '.etb-repeat-live-head a{color:#4d7a32;margin-left:6px;}'
                + '.etb-repeat-live-details ul{margin:0 8px 6px 8px;padding:6px 10px 6px 24px;background:#f4f8f1;border-radius:6px;font:12px/1.6 Arial,sans-serif;color:#374151;}'
                + '.etb-repeat-live-src{list-style:none;margin-left:-14px;color:#8a97a5;font-size:11px;}'
                + '.etb-repeat-live-warn{font:12px/1.4 Arial,sans-serif;color:#8a5a00;padding:0 8px 6px 8px;}'
                + '.etb-repeat-live-empty{margin:4px 8px;padding:14px;border:1px dashed #d6c28a;background:#fffbeb;color:#7a5b00;border-radius:6px;font:13px Arial,sans-serif;}'
                + '.etb-repeat-live-body{pointer-events:none;opacity:.92;}'
                + 'body.etb-parity .etb-repeat-live, body.etb-parity .etb-repeat-fill{display:none !important;}'
                // the builder's editor-helpers.css turns off clicks on every link and button inside a block (so a design's
                // links can't navigate); these two are the canvas's own controls
                + '.etb-repeat-fill, .etb-repeat-live-toggle, .etb-repeat-live-more{pointer-events:auto !important;cursor:pointer;}'
                + '.etb-col{flex:1 1 0;min-width:0;box-sizing:border-box;}'
                // a drop target while empty only; a fixed min-height made a Column of short text taller than the email
                + '.etb-outside:not(:has([data-cbuilder-classname])), .etb-row:not(:has([data-cbuilder-classname])), .etb-repeat-card:not(:has([data-cbuilder-classname])), .etb-col:not(:has([data-cbuilder-classname])){min-height:48px;}'
                + '.etb-col:not(:has(> [data-cbuilder-classname])){outline:1px dashed #b8c7b0;outline-offset:-4px;}'
                + '@media (max-width:480px){.etb-row:not(.etb-row-group){flex-direction:column;}.etb-row:not(.etb-row-group) > .etb-col{flex-basis:auto !important;width:100% !important;}'
                // Stat tiles stack, as in the sent email
                + 'td.etb-tile,td.etb-tile-gap{display:block !important;width:auto !important;}}'
                + '.etb-email:empty:after{content:"Drag blocks here";display:block;padding:80px 0;text-align:center;color:#999;font-family:Arial,sans-serif;}'
                + '[data-etb-inline]{outline:none;}'
                // keeps an emptied text clickable; as a plain min-height it made a 2px divider 16px tall
                + '[data-etb-inline]:empty{min-height:1em;}'
                // the framework pads the region by 5px when inline editing starts, which makes the text jump
                + '[data-etb-inline][data-cbuilder-inlineedit]{padding:0 !important;}'
                // a block that rendered nothing stays selectable; content keeps its real height (a 2px divider stays 2px)
                + '.etb-block:not(:has(*)){min-height:8px;}'
                // email HTML assumes the browser default box model; Bootstrap's border-box would count a cell's padding
                // inside its height (a 300px Hero drew 224px tall)
                + '.etb-block, .etb-block *, .etb-block *::before, .etb-block *::after{box-sizing:content-box;}'
                // canvas == email: blocks are compiled body fragments, so MJML's <head> base styles are missing
                // and the builder iframe's Bootstrap styles leak in (p margin 0 0 16px instead of 13px 0, headings,
                // lists, links). Revert Bootstrap to browser defaults inside blocks, as an email client would, then
                // re-apply MJML's base rules. Inline styles (which MJML emits) still win over these.
                // TEXT elements only: never tables, cells or images. MJML lays those out with HTML attributes
                // (align, width, bgcolor), which any CSS rule overrides, so reverting them breaks bands and centring.
                + '.etb-block :is(p,h1,h2,h3,h4,h5,h6,ul,ol,li,a,blockquote,hr,strong,b,em,i,u,small,code,pre){all:revert;}'
                + '.etb-block p{display:block;margin:13px 0;}'
                + '.etb-block table,.etb-block td{border-collapse:collapse;}'
                + '.etb-drag-placeholder{padding:12px 32px;border:1px dashed #6e9f4b;color:#6e9f4b;font:13px Arial,sans-serif;}'
                // the whole email is one drop container; the framework's default stripes would cover everything
                + 'body.is-dragging .etb-email[data-cbuilder-droparea]:after{background:none;border:2px dashed #6e9f4b;opacity:.5;}'
                + '</style>');

            EmailTemplateBuilder.loadListNames();
            EmailTemplateBuilder.guardViewportSwitch();
            EmailTemplateBuilder.initLiveApply();
            EmailTemplateBuilder.initInlineEditingChrome();
            EmailTemplateBuilder.initTemplateGallery();
            EmailTemplateBuilder.initStyleButton();
            EmailTemplateBuilder.initSizeMeter();

            $.ajax({
                url: EmailTemplateBuilder.serviceUrl('palette'),
                dataType: "json",
                success: function (response) {
                    EmailTemplateBuilder.userEmail = response.userEmail || '';
                    // root component: the email itself. Selecting it (click the background, or "Email style")
                    // shows the theme in the right panel, applied live like any block.
                    CustomBuilder.initPaletteElement("", EmailTemplateBuilder.rootClass, "Email", null, response.themeOptions, null, false, "", {
                        builderTemplate: {
                            'render': EmailTemplateBuilder.renderCanvas,
                            'draggable': false,
                            'movable': false,
                            'deletable': false,
                            'copyable': false,
                            'navigable': false,
                            'supportProperties': true,
                            'supportStyle': false,
                            'childsContainerAttr': 'elements'
                        }
                    });
                    var blocks = response.blocks;
                    var categories = {"content": "Content", "data": "Data", "layout": "Layout", "components": "Components"};
                    for (var i in blocks) {
                        var b = blocks[i];
                        var template = $.extend(true, {}, b.template);
                        if (b.containerType === "row") {
                            template.render = EmailTemplateBuilder.renderRow;
                        } else if (b.containerType === "column") {
                            template.render = EmailTemplateBuilder.renderColumn;
                        } else if (b.containerType === "repeat") {
                            template.render = EmailTemplateBuilder.renderRepeat;
                        } else if (b.containerType === "outside") {
                            template.render = EmailTemplateBuilder.renderOutside;
                        }
                        if (template.elements !== undefined) {
                            // default children (a Row's two Columns) need their own ids
                            template.afterAddElement = function (elementObj) {
                                $.each(elementObj.elements || [], function (j, child) {
                                    CustomBuilder.Builder.updateElementId(child);
                                });
                            };
                        }
                        CustomBuilder.initPaletteElement(categories[b.category] || "Content", b.className, b.label, b.icon, b.propertyOptions,
                                b.defaultProperties, true, "", {builderTemplate: template});
                    }
                    if (callback) {
                        callback();
                    }
                }
            });
        });
    },

    /*
     * Every block gets an id: AI Composer finds the block you select by it, and blocks from a starter (or saved before)
     * have none. Kept with the next save; nothing else changes.
     */
    ensureIds: function (elements) {
        $.each(elements || [], function (i, el) {
            if (el && el.className) {
                el.properties = el.properties || {};
                if (!el.properties.id) {
                    el.properties.id = CustomBuilder.uuid();
                }
                EmailTemplateBuilder.ensureIds(el.elements);
            }
        });
    },

    /* the app's datalists (id to name), for the Repeat's canvas label */
    loadListNames: function () {
        $.getJSON(CustomBuilder.contextPath + '/web/json/console/app/' + CustomBuilder.appId + '/' + CustomBuilder.appVersion + '/datalist/options', function (options) {
            var names = {};
            $.each(options || [], function (i, o) {
                if (o && o.value) {
                    names[o.value] = o.label || o.value;
                }
            });
            EmailTemplateBuilder.listNames = names;
            if (CustomBuilder.Builder.frameBody) {
                CustomBuilder.Builder.frameBody.find('.etb-repeat').each(function () {
                    var data = $(this).data('etbRepeat');
                    if (data) {
                        $(this).attr('data-etb-label', EmailTemplateBuilder.repeatSummary(data.properties || {}));
                    }
                });
            }
        });
    },

    load: function (data) {
        EmailTemplateBuilder.ensureIds((data && data.elements) || []);
        CustomBuilder.Builder.load(data);
        if ($('#etb-data-btn').length) {
            EmailTemplateBuilder.adoptPreviewValues();
        }
        // loading (or a Templates switch) replaces the design without a change event
        EmailTemplateBuilder.updateSize();
    },

    renderCanvas: function (element, elementObj, component, callback) {
        var props = elementObj.properties || {};
        element.html('');
        element.addClass('etb-canvas');
        // data-cbuilder-elements: content blocks drop here; data-cbuilder-rows: Rows (here or in a Repeat's card, never
        // into a column); data-cbuilder-toplevel: Hero and Repeat, only here
        element.append('<div class="etb-email" data-cbuilder-elements data-cbuilder-rows data-cbuilder-toplevel></div>');
        // theme: page background, card background/radius/shadow and width, so the canvas matches the email
        CustomBuilder.Builder.frameBody.css('background-color', EmailTemplateBuilder.color(props.backgroundColor, '#f4f4f4'));
        var card = element.find('.etb-email');
        // the card is drawn per segment (EmailTemplateBuilder.segmentCanvas): "Outside the card" blocks split it
        card[0].style.setProperty('--etb-card-bg', EmailTemplateBuilder.color(props.contentBackground, '#ffffff'));
        card[0].style.setProperty('--etb-card-radius', EmailTemplateBuilder.px(props.contentBorderRadius, '0px'));
        card[0].style.setProperty('--etb-card-shadow', props.contentShadow === 'true' ? '0 2px 10px rgba(0,0,0,0.12)' : 'none');
        EmailTemplateBuilder.watchSegments(card[0]);
        var width = parseInt(props.width, 10);
        if (!isNaN(width)) {
            card.css('max-width', Math.max(320, Math.min(800, width)) + 'px');
        }
        // the theme's link colour (mj-style a{color} in the email)
        var doc = CustomBuilder.Builder.iframe.contentWindow.document;
        var themeStyle = doc.getElementById('etb-theme-style');
        if (!themeStyle) {
            themeStyle = doc.createElement('style');
            themeStyle.id = 'etb-theme-style';
            doc.head.appendChild(themeStyle);
        }
        themeStyle.textContent = '.etb-block a{color:' + EmailTemplateBuilder.color(props.linkColor, '#1a73e8') + ';}';
        callback(element);
    },

    color: function (value, fallback) {
        return /^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$/.test(value || '') ? value : fallback;
    },

    /* a px size or padding; a bare number takes px ("8 32" is 8px 32px), as on the server */
    px: function (value, fallback) {
        var v = $.trim(value || '');
        if (/^\d{1,4}(px)?(\s+\d{1,4}(px)?){0,3}$/.test(v)) {
            v = v.replace(/(\d+)(px)?/g, '$1px').replace(/\s+/g, ' ');
        }
        return /^\d{1,4}px( \d{1,4}px){0,3}$/.test(v) ? v : fallback;
    },

    /* Row: flex container; its Columns are rendered into it by the framework (data-cbuilder-columns) */
    renderRow: function (element, elementObj, component, callback) {
        var p = elementObj.properties || {};
        var row = $('<div class="etb-row" data-cbuilder-columns data-cbuilder-visible></div>');
        if (p.keepSideBySide === 'true') {
            row.addClass('etb-row-group');
        }
        // no colour of its own: leave the background to the card segment (an inline transparent would hide it)
        row.css({
            'background-color': EmailTemplateBuilder.color(p.backgroundColor, ''),
            'padding': EmailTemplateBuilder.px(p.padding, '0px')
        });
        $(element).replaceWith(row);
        callback(row);
    },

    /*
     * Repeat: the card design once; content blocks drop inside (data-cbuilder-elements). The label says what it
     * repeats over. Preview and Send test show one card per row.
     */
    renderRepeat: function (element, elementObj, component, callback) {
        var p = elementObj.properties || {};
        var outer = $('<div class="etb-repeat" data-cbuilder-visible></div>');
        outer.css('padding', EmailTemplateBuilder.px(p.padding, '12px 24px'));
        outer.attr('data-etb-label', EmailTemplateBuilder.repeatSummary(p));
        // content blocks and Rows drop into the card; Hero and Repeat (data-cbuilder-toplevel) don't
        var card = $('<div class="etb-repeat-card" data-cbuilder-elements data-cbuilder-rows></div>');
        if (p.card === 'true') {
            card.css({
                'background-color': EmailTemplateBuilder.color(p.cardBackground, '#f8fafc'),
                'border': '1px solid ' + EmailTemplateBuilder.color(p.borderColor, '#e5e7eb'),
                'border-radius': EmailTemplateBuilder.px(p.cardRadius, '6px'),
                'padding': EmailTemplateBuilder.px(p.cardPadding, '6px 0px')
            });
        }
        // the framework finds the child container inside the element it gets back (loadAndUpdateChildElements)
        outer.append(card);
        // real rows (read-only) under the card; refreshed when the card's blocks change
        var live = $('<div class="etb-repeat-live"></div>').appendTo(outer);
        live.on('mousedown click', '.etb-repeat-live-more', function (e) {
            e.stopPropagation();
            e.preventDefault();
            if (e.type === 'click') {
                var box = live.find('.etb-repeat-live-details');
                box.prop('hidden', !box.prop('hidden'));
                $(this).text(box.prop('hidden') ? 'Details' : 'Hide details');
            }
        });
        live.on('mousedown click', '.etb-repeat-live-toggle', function (e) {
            e.stopPropagation();
            e.preventDefault();
            if (e.type === 'click') {
                EmailTemplateBuilder.setRepeatRowsShown(!EmailTemplateBuilder.repeatRowsShown());
            }
        });
        outer.data('etbRepeat', elementObj);
        EmailTemplateBuilder.syncRepeatFill(outer);
        var timer = null;
        new MutationObserver(function () {
            // blocks added to or deleted from the card: the Fill button comes back once the card is empty again
            EmailTemplateBuilder.syncRepeatFill(outer);
            clearTimeout(timer);
            timer = setTimeout(function () { EmailTemplateBuilder.loadRepeatRows(outer); }, 700);
        }).observe(card[0], {childList: true, subtree: true});
        $(element).replaceWith(outer);
        // the label and the Fill button line up with the strip and card, whatever the Repeat's side padding
        var pad = outer[0].ownerDocument.defaultView.getComputedStyle(outer[0]);
        outer[0].style.setProperty('--etb-pad-l', pad.paddingLeft);
        outer[0].style.setProperty('--etb-pad-r', pad.paddingRight);
        callback(outer);
        EmailTemplateBuilder.loadRepeatRows(outer);
    },

    /* "Fill from columns" while the card is as dropped (empty, or the default title) and a datalist is chosen */
    syncRepeatFill: function (outer) {
        var elementObj = outer.data('etbRepeat');
        var want = !!(elementObj && (elementObj.properties || {}).listId && EmailTemplateBuilder.repeatUntouched(elementObj));
        var button = outer.children('.etb-repeat-fill');
        if (!want) {
            button.remove();
        } else if (!button.length) {
            $('<button type="button" class="etb-repeat-fill" title="Put the datalist\'s columns in the card: the first as a title, the rest as a Facts list">'
                + '&#10024; Fill from columns</button>')
                .on('mousedown click', function (e) { e.stopPropagation(); e.preventDefault(); })
                .on('mouseup', function (e) { e.stopPropagation(); EmailTemplateBuilder.fillRepeat(outer, outer.data('etbRepeat')); })
                .insertAfter(outer.children('.etb-repeat-card'));
        }
    },

    /* the canvas label: which datalist and the row limit (the values its filters used are in the strip's Details) */
    repeatSummary: function (p) {
        if (!p.listId) {
            return '\u21bb Repeat: choose a datalist';
        }
        var name = (EmailTemplateBuilder.listNames || {})[p.listId] || p.listId;
        return '\u21bb Each row of ' + name + ' \u00b7 up to ' + (parseInt(p.maxRows, 10) || 20);
    },

    /* the card as dropped (empty, or only the default {row.id} title) */
    repeatUntouched: function (elementObj) {
        var els = elementObj.elements || [];
        return els.length === 0 || (els.length === 1 && /TextBlock$/.test(els[0].className)
                && ((els[0].properties || {}).html || '') === '<p><strong>{row.id}</strong></p>');
    },

    /* the card from the datalist's columns: the first as a bold title, the rest as a Facts list (empty rows hidden) */
    fillRepeat: function (outer, elementObj) {
        var p = elementObj.properties || {};
        $.getJSON(EmailTemplateBuilder.serviceUrl('rowFields') + '&listId=' + encodeURIComponent(p.listId), function (fields) {
            var seen = {};
            // a datalist can show a column twice; once is enough in the card
            fields = $.grep(fields || [], function (f) {
                var keep = f.name && f.name !== 'id' && !seen[f.name];
                seen[f.name] = true;
                return keep;
            });
            if (!fields.length) {
                EmailTemplateBuilder.toast('This datalist has no columns to fill the card with.');
                return;
            }
            var esc = function (v) { return $('<div>').text(v).html(); };
            var added = [{className: 'org.joget.etb.lib.TextBlock', properties: {
                html: '<p style="margin:0;"><strong>{row.' + fields[0].name + '}</strong></p>', fontSize: '16px', padding: '10px 16px 0px 16px'}}];
            if (fields.length > 1) {
                added.push({className: 'org.joget.etb.lib.FactsBlock', properties: {
                    facts: $.map(fields.slice(1, 9), function (f) { return {label: esc(f.label || f.name), value: '{row.' + f.name + '}'}; }),
                    hideEmpty: 'true', labelStyle: 'caps', valueAlign: 'right', labelWidth: '35%', dividers: 'true', dividerColor: '#eef2f7',
                    labelColor: '#94a3b8', valueColor: '', fontSize: '14px', padding: '4px 16px 10px 16px'}});
            }
            var self = CustomBuilder.Builder;
            $.each(added, function (i, el) { self.updateElementId(el); });
            elementObj.elements = EmailTemplateBuilder.repeatUntouched(elementObj) ? added : (elementObj.elements || []).concat(added);
            self.renderElement(elementObj, outer, self.parseDataToComponent(elementObj), true, null, function (el) {
                self.recursiveCheckVisible(el);
            });
            CustomBuilder.update();
            EmailTemplateBuilder.toast('Filled the card from <strong>' + fields.length + '</strong> columns. Edit or delete the blocks as you like; undo reverts it.', 4000);
        });
    },

    repeatRowsShown: function () {
        try {
            return window.localStorage.getItem('etb.repeatRows') !== 'hidden';
        } catch (e) {
            return true;
        }
    },

    setRepeatRowsShown: function (shown) {
        try {
            window.localStorage.setItem('etb.repeatRows', shown ? 'shown' : 'hidden');
        } catch (e) {
            // not remembered: this page only
        }
        EmailTemplateBuilder.refreshRepeatRows();
    },

    refreshRepeatRows: function () {
        if (CustomBuilder.Builder.frameBody) {
            CustomBuilder.Builder.frameBody.find('.etb-repeat').each(function () { EmailTemplateBuilder.loadRepeatRows($(this)); });
        }
    },

    /* the strip under a Repeat's card: its first rows rendered by the server with the Test data, as in Preview */
    loadRepeatRows: function (outer) {
        var live = outer.children('.etb-repeat-live');
        var elementObj = outer.data('etbRepeat');
        var p = (elementObj && elementObj.properties) || {};
        if (!live.length || (!p.listId && !p.sampleRows)) {
            live.empty().hide();
            return;
        }
        var link = function (cls, label) { return '<a href="#" class="' + cls + '">' + label + '</a>'; };
        if (!EmailTemplateBuilder.repeatRowsShown()) {
            live.removeData('key').show().html('<div class="etb-repeat-live-head">' + link('etb-repeat-live-toggle', 'Show real rows') + '</div>');
            return;
        }
        // what the rows depend on: the Repeat (its settings and blocks), the template settings and the Test data. The
        // card's DOM also changes while typing in a block, before anything is applied; that alone must not re-run the query
        var key = JSON.encode(elementObj) + '|' + JSON.encode((CustomBuilder.data && CustomBuilder.data.properties) || {})
                + '|' + (EmailTemplateBuilder.testRecordId || '') + '|' + JSON.stringify(EmailTemplateBuilder.testVariables || {});
        if (live.data('key') === key) {
            return;
        }
        live.data('key', key);
        var seq = (live.data('seq') || 0) + 1;
        live.data('seq', seq).show().addClass('loading');
        $.ajax({
            type: 'POST',
            url: EmailTemplateBuilder.serviceUrl('repeatRows'),
            data: {json: JSON.encode(elementObj), templateProperties: JSON.encode((CustomBuilder.data && CustomBuilder.data.properties) || {}),
                etbRecordId: EmailTemplateBuilder.testRecordId || '', etbVariables: JSON.stringify(EmailTemplateBuilder.testVariables || {})},
            dataType: 'json',
            beforeSend: function (request) {
                request.setRequestHeader(ConnectionManager.tokenName, ConnectionManager.tokenValue);
            },
            success: function (r) {
                if (live.data('seq') !== seq) {
                    return;
                }
                var esc = function (v) { return $('<div>').text(v).html(); };
                var note = (r.notes || [])[0];
                // one quiet line when it works; the values used stay behind Details, opened by themselves when nothing matches
                var head = '<div class="etb-repeat-live-head"><strong>Real rows</strong>';
                var details = '';
                var empty = false;
                if (note) {
                    empty = note.found === 0;
                    head += ': ' + (empty ? 'no rows match' : note.found + ' found' + (note.found > 3 ? ', showing the first 3' : ''));
                    if ((note.values || []).length) {
                        details = '<ul>' + $.map(note.values, function (v) { return '<li>' + esc(v) + '</li>'; }).join('')
                            + '<li class="etb-repeat-live-src">Values from Test data</li></ul>';
                        head += ' \u00b7 ' + link('etb-repeat-live-more', empty ? 'Hide details' : 'Details');
                    }
                }
                head += ' \u00b7 ' + link('etb-repeat-live-toggle', 'Hide') + '</div>';
                if (note && note.found > 0 && note.found >= note.max) {
                    head += '<div class="etb-repeat-live-warn">That is the limit (' + note.max + '), so there may be more. Raise <em>Maximum rows</em> to include them.</div>';
                }
                if (details) {
                    head += '<div class="etb-repeat-live-details"' + (empty ? '' : ' hidden') + '>' + details + '</div>';
                }
                var problems = (r.warnings || []).map(esc).join('<br>') || (r.error ? 'Could not load the rows: ' + esc(r.error) : '');
                if (problems) {
                    head += '<div class="etb-repeat-live-warn">' + problems + '</div>';
                }
                var body = empty ? '<div class="etb-repeat-live-empty">No rows match these values. Check the Test data (toolbar) or the datalist\u2019s filters.</div>'
                        : '<div class="etb-repeat-live-body">' + (r.html || '') + '</div>';
                live.removeClass('loading').html(head + body);
                // the rendered rows carry the Repeat's own side padding, and the strip already sits inside it on the canvas:
                // pull them out by that padding so their cards line up with the card above
                var cs = outer[0].ownerDocument.defaultView.getComputedStyle(outer[0]);
                live.children('.etb-repeat-live-body').css({'margin-left': '-' + cs.paddingLeft, 'margin-right': '-' + cs.paddingRight});
            },
            error: function (xhr) {
                live.removeData('key');
                if (live.data('seq') === seq) {
                    live.removeClass('loading').html('<div class="etb-repeat-live-head">Could not load the rows (' + xhr.status + '). ' + link('etb-repeat-live-toggle', 'Hide') + '</div>');
                }
            }
        });
    },

    /* Outside the card: blocks and Rows on the page background; it splits the card (segmentCanvas) */
    renderOutside: function (element, elementObj, component, callback) {
        var p = elementObj.properties || {};
        var box = $('<div class="etb-outside" data-cbuilder-elements data-cbuilder-rows data-cbuilder-visible></div>');
        box.css({
            'padding': EmailTemplateBuilder.px(p.padding, '24px 0px'),
            'background-color': EmailTemplateBuilder.color(p.backgroundColor, 'transparent')
        });
        $(element).replaceWith(box);
        callback(box);
    },

    /*
     * Mark the card's segments: runs of top-level blocks between "Outside the card" blocks get the card look,
     * rounded and shadowed at their ends, and the 24px page margin when a card starts or ends the email,
     * as the email's card wrappers do. Re-run whenever the top level changes.
     */
    segmentCanvas: function (email) {
        var items = $(email).children('[data-cbuilder-classname]');
        items.removeClass('etb-seg etb-seg-first etb-seg-last etb-page-first etb-page-last');
        items.each(function (i) {
            if ($(this).hasClass('etb-outside')) {
                return;
            }
            var prev = items.eq(i - 1), next = items.eq(i + 1);
            $(this).addClass('etb-seg');
            if (i === 0 || prev.hasClass('etb-outside')) {
                $(this).addClass('etb-seg-first');
            }
            if (i === items.length - 1 || next.hasClass('etb-outside')) {
                $(this).addClass('etb-seg-last');
            }
            if (i === 0) {
                $(this).addClass('etb-page-first');
            }
            if (i === items.length - 1) {
                $(this).addClass('etb-page-last');
            }
        });
    },

    watchSegments: function (email) {
        if (email.etbObserver) {
            email.etbObserver.disconnect();
        }
        var pending = false;
        var run = function () {
            pending = false;
            EmailTemplateBuilder.segmentCanvas(email);
        };
        email.etbObserver = new MutationObserver(function () {
            if (!pending) {
                pending = true;
                window.requestAnimationFrame(run);
            }
        });
        email.etbObserver.observe(email, {childList: true});
        run();
    },

    /* Column: holds content blocks (data-cbuilder-elements), styled like its mj-column */
    renderColumn: function (element, elementObj, component, callback) {
        var p = elementObj.properties || {};
        var col = $('<div class="etb-col" data-cbuilder-elements data-cbuilder-visible></div>');
        var width = (p.width || '').trim();
        if (/^\d{1,3}(%|px)$/.test(width)) {
            col.css('flex', '0 0 ' + width);
        }
        col.css({
            'background-color': EmailTemplateBuilder.color(p.backgroundColor, 'transparent'),
            'padding': EmailTemplateBuilder.px(p.padding, '0px'),
            'border-radius': EmailTemplateBuilder.px(p.borderRadius, '0px'),
            'align-self': {'middle': 'center', 'bottom': 'flex-end'}[p.verticalAlign] || 'flex-start'
        });
        if (EmailTemplateBuilder.color(p.borderColor, '') !== '') {
            col.css('border', EmailTemplateBuilder.px(p.borderWidth, '1px') + ' solid ' + p.borderColor);
        }
        $(element).replaceWith(col);
        callback(col);
    },

    /* each block is compiled by the server's mjml4j renderer, with the template theme */
    renderBlock: function (element, elementObj, component, callback) {
        $.ajax({
            type: "POST",
            url: EmailTemplateBuilder.serviceUrl('render'),
            data: {
                "json": JSON.encode(elementObj),
                "templateProperties": JSON.encode((CustomBuilder.data && CustomBuilder.data.properties) || {})
            },
            dataType: "text",
            beforeSend: function (request) {
                request.setRequestHeader(ConnectionManager.tokenName, ConnectionManager.tokenValue);
            },
            success: function (response) {
                EmailTemplateBuilder.destroyInlineEditors(element);
                // data-cbuilder-visible: the framework flags any block without text/links/images (Divider,
                // Spacer) as invisible; every compiled block is visible, so skip that check
                var newElement = $('<div class="etb-block" data-cbuilder-visible></div>').html(response);
                $(element).replaceWith(newElement);
                callback(newElement);
            },
            error: function (xhr) {
                var newElement = $('<div class="etb-block" style="color:#c00;padding:8px 32px;font:13px Arial,sans-serif;">Render failed (' + xhr.status + ')</div>');
                $(element).replaceWith(newElement);
                callback(newElement);
            }
        });
    },

    /*
     * CustomBuilder.viewport() sets body.no-right-panel, and while that is set the framework skips its
     * unapplied-changes check, so a pending (e.g. inline-typed) edit is silently dropped on the next
     * selection. Apply pending changes before the viewport switches.
     */
    guardViewportSwitch: function () {
        $(document).off('mousedown.etb').on('mousedown.etb', '.responsive-buttons button', function () {
            var pending = false;
            $(".element-properties .property-editor-container").each(function () {
                var editor = $(this).parent().data("editor");
                if (editor !== undefined && !editor.saved && editor.isChange()) {
                    pending = true;
                }
            });
            if (pending && CustomBuilder.getBuilderSetting("autoApplyChanges")) {
                CustomBuilder.applyElementProperties();
            }
        });
    },

    /* destroy the framework's inline TinyMCE inside a block before its DOM is replaced, else it leaks */
    destroyInlineEditors: function (element) {
        var win = CustomBuilder.Builder.iframe.contentWindow;
        if (win && win.tinymce) {
            $(element).find('[id]').addBack('[id]').each(function () {
                var ed = win.tinymce.get(this.id);
                if (ed) {
                    ed.destroy();
                }
            });
        }
    },

    /* ---------- Templates gallery: switch the design of an existing template (undoable) ---------- */

    initTemplateGallery: function () {
        if ($('#etb-templates-btn').length === 0) {
            $('#builderToolbar #main-button-group').after('<div class="btn-group toolbar-group me-3" role="group">'
                + '<button class="btn btn-light" id="etb-templates-btn" type="button" title="Templates: start from a ready-made design">'
                + '<i class="las la-th-large"></i> <span class="etb-btn-label">Templates</span></button></div>');
            $('#etb-templates-btn').on('click', EmailTemplateBuilder.openGallery);
            $('#etb-templates-btn').after('<button class="btn btn-light" id="etb-components-btn" type="button" title="Components: insert a ready-made part (steps, progress bars, callouts, footers)">'
                + '<i class="las la-puzzle-piece"></i> <span class="etb-btn-label">Components</span></button>');
            $('#etb-components-btn').on('click', EmailTemplateBuilder.openComponents);
        }
        if ($('#etb-gallery').length === 0) {
            $('head').append('<style id="etb-toolbar-style">'
                // like the builder's own buttons: icon only (label in the tooltip) unless the toolbar has room
                + '#builderToolbar .etb-btn-label{display:none;margin-left:4px;}'
                + '@media (min-width:1600px){#builderToolbar .etb-btn-label{display:inline !important;}}'
                + '#etb-data-btn{position:relative;}'
                + '#etb-data-btn .etb-data-dot{display:none;position:absolute;top:4px;left:22px;width:8px;height:8px;border-radius:50%;background:#6e9f4b;box-shadow:0 0 0 2px #fff;}'
                + '#etb-data-btn.etb-has-data .etb-data-dot{display:block;}'
                + '</style>');
            $('head').append('<style id="etb-gallery-style">'
                + '.etb-gallery{display:none;position:fixed;inset:0;z-index:20000;background:rgba(15,23,42,.55);align-items:stretch;justify-content:center;padding:24px;}'
                + '.etb-gallery.open{display:flex;}'
                + '.etb-gallery .etb-panel{background:#fff;border-radius:8px;width:100%;max-width:1040px;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 10px 40px rgba(0,0,0,.3);color:#111827;}'
                + '.etb-gallery .etb-head{display:flex;justify-content:space-between;align-items:center;padding:12px 16px;border-bottom:1px solid #e5e7eb;font-size:16px;}'
                + '.etb-gallery .etb-head small{display:block;color:#6b7280;font-weight:normal;font-size:12px;}'
                + '.etb-gallery .etb-close{border:0;background:none;font-size:26px;line-height:1;cursor:pointer;color:#6b7280;}'
                + '.etb-gallery .etb-tabs{display:flex;gap:6px;flex-wrap:wrap;padding:10px 16px;border-bottom:1px solid #f1f5f9;}'
                + '.etb-gallery .etb-tabs button{border:1px solid #d1d5db;background:#fff;border-radius:16px;padding:4px 12px;cursor:pointer;font-size:13px;color:#111827;}'
                + '.etb-gallery .etb-tabs button.active{background:#6e9f4b;border-color:#6e9f4b;color:#fff;}'
                + '.etb-gallery .etb-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:16px;padding:16px;overflow-y:auto;}'
                + '.etb-gallery .etb-card{display:flex;flex-direction:column;text-align:left;border:2px solid #e5e7eb;border-radius:8px;padding:8px;background:#fff;cursor:pointer;}'
                + '.etb-gallery .etb-card:hover,.etb-gallery .etb-card:focus{border-color:#6e9f4b;outline:none;}'
                + '.etb-gallery .etb-card.hidden{display:none;}'
                + '.etb-gallery .etb-thumb{display:block;width:100%;height:250px;overflow:hidden;border-radius:4px;background:#eef2f6;position:relative;}'
                // a 640px email scaled to 30%, anchored at the card's horizontal centre
                + '.etb-gallery .etb-thumb iframe{position:absolute;top:0;left:50%;margin-left:-320px;width:640px;height:840px;border:0;transform:scale(.3);transform-origin:50% 0;pointer-events:none;}'
                + '.etb-gallery .etb-label{font-weight:bold;margin-top:8px;font-size:14px;}'
                + '.etb-gallery .etb-cat{font-size:11px;color:#6e9f4b;text-transform:uppercase;letter-spacing:.04em;margin-top:2px;}'
                + '.etb-gallery .etb-desc{font-size:12px;color:#6b7280;margin-top:4px;line-height:1.35;}'
                + '</style>');
            $('body').append('<div id="etb-gallery" class="etb-gallery" role="dialog" aria-modal="true" aria-label="Templates"><div class="etb-panel">'
                + '<div class="etb-head"><div><strong>Templates</strong><small>Pick a design to replace the current one. You can undo it.</small></div>'
                + '<button type="button" class="etb-close" aria-label="Close">&times;</button></div>'
                + '<div class="etb-tabs"></div><div class="etb-grid"></div></div></div>');
            var gallery = $('#etb-gallery');
            gallery.on('click', '.etb-close', EmailTemplateBuilder.closeGallery);
            gallery.on('click', function (e) { if (e.target === this) { EmailTemplateBuilder.closeGallery(); } });
            gallery.on('click', '.etb-tabs button', function () { EmailTemplateBuilder.filterGallery($(this)); });
            gallery.on('click', '.etb-card', function () { EmailTemplateBuilder.chooseStarter($(this).data('id'), $(this).data('label')); });
            $(document).on('keydown.etbgallery', function (e) { if (e.key === 'Escape' && gallery.hasClass('open')) { EmailTemplateBuilder.closeGallery(); } });
        }
        EmailTemplateBuilder.initComponentGallery();
    },

    openGallery: function () {
        var gallery = $('#etb-gallery');
        gallery.addClass('open');
        if (gallery.data('loaded')) {
            return;
        }
        $.ajax({
            url: EmailTemplateBuilder.serviceUrl('starters'),
            dataType: 'json',
            success: function (index) {
                var tabs = gallery.find('.etb-tabs').empty().append('<button type="button" class="active" data-cat="">All</button>');
                var grid = gallery.find('.etb-grid').empty();
                var seen = {};
                $.each(index.starters, function (i, s) {
                    if (!seen[s.category]) {
                        seen[s.category] = true;
                        tabs.append($('<button type="button"></button>').attr('data-cat', s.category).text(s.category));
                    }
                    var card = $('<button type="button" class="etb-card"></button>').attr('data-id', s.id).attr('data-label', s.label).attr('data-cat', s.category);
                    var frame = $('<iframe tabindex="-1" sandbox="allow-same-origin" loading="lazy"></iframe>').attr('title', s.label)
                            .attr('src', EmailTemplateBuilder.serviceUrl('starterPreview') + '&id=' + encodeURIComponent(s.id));
                    card.append($('<span class="etb-thumb"></span>').append(frame));
                    card.append($('<span class="etb-label"></span>').text(s.label));
                    card.append($('<span class="etb-cat"></span>').text(s.category));
                    card.append($('<span class="etb-desc"></span>').text(s.description));
                    grid.append(card);
                });
                gallery.data('loaded', true);
            }
        });
    },

    closeGallery: function () {
        $('#etb-gallery').removeClass('open');
    },

    filterGallery: function (tab) {
        var cat = tab.attr('data-cat');
        tab.addClass('active').siblings().removeClass('active');
        $('#etb-gallery .etb-card').each(function () {
            $(this).toggleClass('hidden', cat !== '' && $(this).attr('data-cat') !== cat);
        });
    },

    /* replace the design with a starter, keeping this template's id, name and description; undoable */
    chooseStarter: function (id, label) {
        var elements = (CustomBuilder.data && CustomBuilder.data.elements) || [];
        var untouched = elements.length === 0 || (elements.length === 1 && /pick a ready-made design/.test(((elements[0].properties || {}).html) || ''));
        var confirmed = untouched ? Promise.resolve(true)
                : (window.UI && UI.asyncConfirm ? UI.asyncConfirm('Replace the current design with "' + label + '"? You can undo this.')
                        : Promise.resolve(window.confirm('Replace the current design with "' + label + '"? You can undo this.')));
        confirmed.then(function (ok) {
            if (!ok) {
                return;
            }
            $.ajax({
                url: EmailTemplateBuilder.serviceUrl('starter') + '&id=' + encodeURIComponent(id),
                dataType: 'json',
                success: function (starter) {
                    var current = (CustomBuilder.data && CustomBuilder.data.properties) || {};
                    starter.properties = $.extend({}, starter.properties, {
                        id: current.id, name: current.name, description: current.description
                    });
                    EmailTemplateBuilder.closeGallery();
                    CustomBuilder.loadJson(JSON.encode(starter), true);
                    EmailTemplateBuilder.seedTestVariables(starter);
                }
            });
        });
    },

    /* A picked starter's variables start with the gallery's sample values as Test data (only ones without a value). */
    seedTestVariables: function (starter) {
        $.getJSON(EmailTemplateBuilder.serviceUrl('starters'), function (index) {
            var samples = index.variables || {};
            var values = $.extend({}, EmailTemplateBuilder.testVariables || {});
            var changed = false;
            $.each(((starter.properties || {}).variables) || [], function (i, v) {
                if (v && v.name && !values[v.name] && samples[v.name] !== undefined) {
                    values[v.name] = samples[v.name];
                    changed = true;
                }
            });
            if (changed) {
                EmailTemplateBuilder.useTestData(EmailTemplateBuilder.testRecordId || '', values);
            }
        });
    },

    /*
     * The AI Composer's sample values for the variables it declared (or a starter it used brought): into Test data,
     * as a picked starter's are. A value the user typed stays; one the composer set earlier on this page follows its
     * newer sample.
     */
    useComposerValues: function (samples) {
        var declared = {};
        $.each(EmailTemplateBuilder.declaredVariables(), function (i, v) {
            declared[$.trim(v.name)] = true;
        });
        var values = $.extend({}, EmailTemplateBuilder.testVariables || {});
        var seeded = EmailTemplateBuilder.composerValues = EmailTemplateBuilder.composerValues || {};
        var changed = false;
        $.each(samples || {}, function (name, sample) {
            if (!declared[name] || typeof sample !== 'string' || sample === '') {
                return;
            }
            if (!values[name] || values[name] === seeded[name]) {
                changed = changed || values[name] !== sample;
                values[name] = sample;
            }
            if (values[name] === sample) {
                seeded[name] = sample;
            }
        });
        if (changed) {
            EmailTemplateBuilder.useTestData(EmailTemplateBuilder.testRecordId || '', values);
        }
    },

    /* ---------- Components gallery: insert a ready-made part into the current design (undoable) ---------- */

    initComponentGallery: function () {
        if ($('#etb-components').length > 0) {
            return;
        }
        $('head').append('<style id="etb-components-style">'
            + '#etb-components .etb-grid{grid-template-columns:repeat(auto-fill,minmax(340px,1fr));align-items:start;}'
            // sized to the component on load (EmailTemplateBuilder.fitComponentThumb): a short strip, not a page
            + '#etb-components .etb-thumb{height:120px;}'
            + '#etb-components .etb-thumb iframe{left:0;margin-left:0;height:600px;transform-origin:0 0;}'
            + '</style>');
        $('body').append('<div id="etb-components" class="etb-gallery" role="dialog" aria-modal="true" aria-label="Components"><div class="etb-panel">'
            + '<div class="etb-head"><div><strong>Components</strong><small>Inserted after the selected block, or at the end of the card. Edit it in its properties. You can undo it.</small></div>'
            + '<button type="button" class="etb-close" aria-label="Close">&times;</button></div>'
            + '<div class="etb-tabs"></div><div class="etb-grid"></div></div></div>');
        var dialog = $('#etb-components');
        var close = function () { dialog.removeClass('open'); };
        dialog.on('click', '.etb-close', close);
        dialog.on('click', function (e) { if (e.target === this) { close(); } });
        dialog.on('click', '.etb-tabs button', function () {
            var cat = $(this).attr('data-cat');
            $(this).addClass('active').siblings().removeClass('active');
            dialog.find('.etb-card').each(function () {
                $(this).toggleClass('hidden', cat !== '' && $(this).attr('data-cat') !== cat);
            });
        });
        dialog.on('click', '.etb-card', function () {
            var card = $(this);
            close();
            $.getJSON(EmailTemplateBuilder.serviceUrl('component') + '&id=' + encodeURIComponent(card.data('id')), function (component) {
                EmailTemplateBuilder.insertComponent(component.element, component.place, card.data('label'));
            });
        });
        $(document).on('keydown.etbcomponents', function (e) { if (e.key === 'Escape' && dialog.hasClass('open')) { close(); } });
    },

    openComponents: function () {
        var dialog = $('#etb-components');
        dialog.addClass('open');
        if (dialog.data('loaded')) {
            return;
        }
        $.getJSON(EmailTemplateBuilder.serviceUrl('components'), function (index) {
            var tabs = dialog.find('.etb-tabs').empty().append('<button type="button" class="active" data-cat="">All</button>');
            var grid = dialog.find('.etb-grid').empty();
            var seen = {};
            $.each(index.components, function (i, c) {
                if (!seen[c.category]) {
                    seen[c.category] = true;
                    tabs.append($('<button type="button"></button>').attr('data-cat', c.category).text(c.category));
                }
                var card = $('<button type="button" class="etb-card"></button>').attr('data-id', c.id).attr('data-label', c.label).attr('data-cat', c.category);
                var frame = $('<iframe tabindex="-1" sandbox="allow-same-origin" loading="lazy"></iframe>').attr('title', c.label)
                        .on('load', function () { EmailTemplateBuilder.fitComponentThumb(this); })
                        .attr('src', EmailTemplateBuilder.serviceUrl('componentPreview') + '&id=' + encodeURIComponent(c.id));
                card.append($('<span class="etb-thumb"></span>').append(frame));
                card.append($('<span class="etb-label"></span>').text(c.label));
                card.append($('<span class="etb-cat"></span>').text(c.category));
                card.append($('<span class="etb-desc"></span>').text(c.description));
                grid.append(card);
            });
            dialog.data('loaded', true);
        });
    },

    /* scale the 640px-wide preview to the card's width, and the thumbnail to the component's height */
    fitComponentThumb: function (frame) {
        try {
            var thumb = $(frame).parent();
            var scale = thumb.width() / 640;
            // measured in a 1px-high frame: a taller frame reports its own height, not the content's
            $(frame).css('height', '1px');
            var height = frame.contentDocument.documentElement.scrollHeight;
            $(frame).css({height: height + 'px', transform: 'scale(' + scale + ')'});
            thumb.css('height', Math.min(260, Math.ceil(height * scale)) + 'px');
        } catch (e) {
            // not same-origin (should not happen): keep the default strip
        }
    },

    /*
     * Insert a component (one element, with its children) like the builder's own paste: after the selected block
     * at the first level that accepts it (a Steps block selected inside a column goes after it in the column; a Row
     * goes after the Row that holds the selection), or into a selected Column. With nothing selected it goes at the
     * end of the card, before the blocks under it. A component that places itself "first" or "last" (a logo above
     * the card, a footer) always goes at the very start or end of the email.
     */
    insertComponent: function (obj, place, label) {
        var self = CustomBuilder.Builder;
        var component = self.parseDataToComponent(obj);
        if (!component) {
            EmailTemplateBuilder.toast('This component needs a block that is not installed.');
            return;
        }
        var attr = component.builderTemplate.getParentContainerAttr(obj, component);
        var holder = component.builderTemplate.getParentDataHolder(obj, component);
        var email = self.frameBody.find('.etb-email');
        var temp = $('<div></div>');
        // a component that places itself (logo, footer) ignores the selection, which after an insert is the new block
        var selected = self.selectedEl && !place ? $(self.selectedEl) : $();
        var anchor = selected;
        while (anchor.length && !anchor.hasClass('etb-canvas') && !anchor.parent().is('[data-cbuilder-' + attr + ']')) {
            anchor = anchor.parent().closest('[data-cbuilder-classname]');
        }
        self.updateElementId(obj);
        if (selected.is('.etb-col[data-cbuilder-' + attr + ']')) {
            var data = selected.data('data');
            (data[holder] = data[holder] || []).push(obj);
            selected.append(temp);
        } else if (anchor.length && !anchor.hasClass('etb-canvas')) {
            var parentData = anchor.parent().closest('[data-cbuilder-classname]').data('data');
            var siblings = parentData[holder] = parentData[holder] || [];
            siblings.splice($.inArray(anchor.data('data'), siblings) + 1, 0, obj);
            anchor.after(temp);
        } else {
            var elements = CustomBuilder.data.elements = CustomBuilder.data.elements || [];
            var at = elements.length;
            if (place === 'first') {
                at = 0;
            } else if (place !== 'last') {
                // inside the card: before a trailing run of "Outside the card" blocks (the footer)
                while (at > 0 && /OutsideBlock$/.test(elements[at - 1].className)) {
                    at--;
                }
            }
            var next = email.children('[data-cbuilder-classname]').eq(at);
            if (next.length) {
                next.before(temp);
            } else {
                email.append(temp);
            }
            elements.splice(at, 0, obj);
        }
        self.component = component;
        self.renderElement(obj, temp, component, true, null, function (element) {
            if ($(element).find('[data-cbuilder-classname]').length > 0) {
                self.recursiveCheckVisible(element);
            }
            if (element && element[0] && element[0].scrollIntoView) {
                element[0].scrollIntoView({block: 'nearest', behavior: 'smooth'});
            }
        });
        CustomBuilder.update();
        EmailTemplateBuilder.toast('Inserted <strong>' + $('<span>').text(label || component.label).html() + '</strong>. Edit it in its properties, or undo.', 3500);
    },

    /* ---------- live editing ---------- */

    /*
     * The framework applies a changed property only on deselect or "Apply Change". Apply on each field's
     * change event instead (debounced), so colours, corners and shadow show immediately. "change" fires on
     * blur for text fields, so typing is never interrupted.
     */
    initLiveApply: function () {
        var timer = null;
        var schedule = function () {
            if (!CustomBuilder.getBuilderSetting("autoApplyChanges")) {
                return;
            }
            clearTimeout(timer);
            timer = setTimeout(function () {
                CustomBuilder.applyElementProperties();
            }, 250);
        };
        $(document).off('change.etblive').on('change.etblive', '.element-properties input, .element-properties select, .element-properties textarea', schedule);
        // rich-text (htmleditor) fields are TinyMCE instances: their edits never fire the textarea's change event
        if (window.tinymce && !EmailTemplateBuilder.tinymceHooked) {
            EmailTemplateBuilder.tinymceHooked = true;
            window.tinymce.on('AddEditor', function (e) {
                var editor = e.editor;
                editor.on('init', function () {
                    if ($(editor.getElement()).closest('.element-properties').length > 0) {
                        editor.on('change', schedule);
                    }
                });
            });
        }
    },

    /* Settings tab saved: keep the data and redraw, so the canvas never needs a refresh */
    saveBuilderProperties: function (container, properties) {
        $.extend(CustomBuilder.data.properties, properties);
        CustomBuilder.update();
        CustomBuilder.Builder.load(CustomBuilder.data);
    },

    /* "Email style": select the email itself, whose properties are the theme */
    initStyleButton: function () {
        if ($('#etb-style-btn').length === 0) {
            $('#etb-templates-btn').closest('.btn-group').append('<button class="btn btn-light" id="etb-style-btn" type="button" '
                + 'title="Colours, corners, shadow, font and width of the whole email"><i class="las la-palette"></i> '
                + '<span class="etb-btn-label">Email style</span></button>');
            $('#etb-style-btn').on('click', function () {
                var root = CustomBuilder.Builder.frameBody.find('.etb-canvas').first();
                if (root.length) {
                    CustomBuilder.Builder.selectNode(root);
                }
            });
        }
        if ($('#etb-test-btn').length === 0) {
            $('#etb-style-btn').after('<button class="btn btn-light" id="etb-test-btn" type="button" '
                + 'title="Email the current design (saved or not) to an address you choose, merge fields filled as in Preview">'
                + '<i class="las la-paper-plane"></i> <span class="etb-btn-label">Send test</span></button>');
            $('#etb-test-btn').on('click', EmailTemplateBuilder.sendTest);
        }
        EmailTemplateBuilder.initTestData();
    },

    /*
     * A short notice at the top of the builder. CustomBuilder.showMessage writes to #builder-message,
     * which has no size in this layout, so its messages are never seen.
     */
    toast: function (html, ms) {
        var t = $('#etb-toast');
        if (t.length === 0) {
            t = $('<div id="etb-toast" role="status" aria-live="polite"></div>').appendTo('body');
            $('head').append('<style>#etb-toast{display:none;position:fixed;top:72px;left:50%;transform:translateX(-50%);z-index:20001;'
                + 'max-width:min(560px,calc(100vw - 32px));background:#1f2937;color:#fff;font:13px/1.45 Arial,sans-serif;padding:10px 16px;'
                + 'border-radius:6px;box-shadow:0 6px 24px rgba(0,0,0,.25);cursor:pointer;}</style>');
            t.on('click', function () { t.stop(true).fadeOut(150); });
        }
        clearTimeout(EmailTemplateBuilder.toastTimer);
        t.html(html).stop(true).fadeIn(150);
        EmailTemplateBuilder.toastTimer = setTimeout(function () { t.fadeOut(300); }, ms || 4000);
    },

    /* ---------- size meter: the real email's HTML size against Gmail's ~102 KB clipping limit ---------- */

    initSizeMeter: function () {
        if ($('#etb-size').length > 0) {
            return;
        }
        $('head').append('<style id="etb-size-style">'
            + '#etb-size{position:absolute;left:12px;bottom:12px;z-index:5;display:block;width:auto;font:12px/1.4 Arial,sans-serif;color:#374151;background:rgba(255,255,255,.92);'
            + 'border:1px solid #d1d5db;border-radius:12px;padding:3px 10px;box-shadow:0 1px 3px rgba(0,0,0,.08);cursor:default;pointer-events:auto;}'
            + '#etb-size .etb-size-bar{display:inline-block;vertical-align:middle;width:48px;height:5px;border-radius:3px;background:#e5e7eb;margin-left:6px;overflow:hidden;}'
            + '#etb-size .etb-size-bar i{display:block;height:100%;background:#6e9f4b;}'
            + '#etb-size.warn{border-color:#f59e0b;color:#92400e;}#etb-size.warn .etb-size-bar i{background:#f59e0b;}'
            + '#etb-size.clip{border-color:#dc2626;color:#991b1b;}#etb-size.clip .etb-size-bar i{background:#dc2626;}'
            // the explanation: a child of the pill, so it stays open while the pointer is over either (a title tooltip closes on its own)
            + '#etb-size .etb-size-tip{display:none;position:absolute;left:0;bottom:calc(100% + 6px);width:300px;max-width:calc(100vw - 48px);'
            + 'background:#1f2937;color:#fff;border-radius:6px;padding:8px 12px;line-height:1.45;box-shadow:0 6px 24px rgba(0,0,0,.25);white-space:normal;}'
            + '#etb-size .etb-size-tip::after{content:"";position:absolute;left:18px;top:100%;border:6px solid transparent;border-top-color:#1f2937;}'
            + '#etb-size .etb-size-tip::before{content:"";position:absolute;left:0;right:0;top:100%;height:8px;}'
            + '#etb-size:hover .etb-size-tip,#etb-size:focus .etb-size-tip,#etb-size:focus-within .etb-size-tip{display:block;}'
            + '#etb-size:focus{outline:2px solid #6e9f4b;outline-offset:2px;}'
            + '</style>');
        // an <aside>, not a <div>: the builder makes every div in #builder_canvas position:relative
        $('#builder_canvas').append('<aside id="etb-size" tabindex="0" aria-describedby="etb-size-tip">'
            + '<span class="etb-size-value" role="status" aria-live="polite">Email size …</span>'
            + '<span class="etb-size-tip" id="etb-size-tip" role="tooltip"></span></aside>');
        var timer = null;
        $('#cbuilder-json').off('change.etbsize').on('change.etbsize', function () {
            clearTimeout(timer);
            timer = setTimeout(EmailTemplateBuilder.updateSize, 1200);
        });
        EmailTemplateBuilder.updateSize();
    },

    updateSize: function () {
        if (EmailTemplateBuilder.sizeXhr) {
            EmailTemplateBuilder.sizeXhr.abort();
        }
        EmailTemplateBuilder.sizeXhr = $.ajax({
            type: 'POST',
            url: EmailTemplateBuilder.serviceUrl('size'),
            data: {json: JSON.encode(CustomBuilder.data), etbRecordId: EmailTemplateBuilder.testRecordId || '',
                etbVariables: JSON.stringify(EmailTemplateBuilder.testVariables || {})},
            dataType: 'json',
            beforeSend: function (request) {
                request.setRequestHeader(ConnectionManager.tokenName, ConnectionManager.tokenValue);
            },
            success: function (r) {
                var meter = $('#etb-size');
                var value = meter.find('.etb-size-value');
                var tip = meter.find('.etb-size-tip');
                if (r.bytes < 0) {
                    meter.removeClass('warn clip');
                    value.text('Email size: not available');
                    tip.text('The email could not be rendered. Open Preview for details.');
                    return;
                }
                var kb = function (b) { return Math.max(1, Math.round(b / 1024)); };
                var pct = Math.min(100, Math.round(r.bytes * 100 / r.clip));
                var state = r.bytes >= r.clip ? 'clip' : (r.bytes >= r.warn ? 'warn' : '');
                meter.removeClass('warn clip').addClass(state);
                value.html('Email size ' + kb(r.bytes) + ' KB <span class="etb-size-bar"><i style="width:' + pct + '%"></i></span>');
                tip.text('The sent email\'s HTML is about ' + kb(r.bytes) + ' KB' + (EmailTemplateBuilder.testRecordId ? ' with the test record' : '') + '. '
                        + 'Gmail cuts off emails over about ' + kb(r.clip) + ' KB behind a "View entire message" link, which also hides the end of the email. '
                        + (state === 'clip' ? 'This email will be clipped: remove content or split it.'
                            : state === 'warn' ? 'Getting close: leave room for merge fields and footers added by your mail server.' : 'Plenty of room.'));
            },
            complete: function () {
                EmailTemplateBuilder.sizeXhr = null;
            }
        });
    },

    /* ---------- Test data: a form record whose values fill #form.table.column# in Preview and Send test ---------- */

    testRecordKey: function () {
        return 'etb.testRecord.' + CustomBuilder.appId + '.' + CustomBuilder.id;
    },

    initTestData: function () {
        if ($('#etb-data-btn').length > 0) {
            return;
        }
        EmailTemplateBuilder.testRecordId = '';
        EmailTemplateBuilder.testVariables = {};
        try {
            EmailTemplateBuilder.testRecordId = window.localStorage.getItem(EmailTemplateBuilder.testRecordKey()) || '';
            var saved = window.localStorage.getItem(EmailTemplateBuilder.testVariablesKey());
            if (saved) {
                EmailTemplateBuilder.testVariables = JSON.parse(saved) || {};
            }
        } catch (e) {
            // private window or blocked storage: test data lasts for this page only
        }
        EmailTemplateBuilder.adoptPreviewValues();
        // Repeats drawn before the test data was read: reload their real rows with it
        EmailTemplateBuilder.refreshRepeatRows();
        $('#etb-test-btn').before('<button class="btn btn-light" id="etb-data-btn" type="button" '
            + 'title="A form record whose values fill the #form...# merge fields in Preview and Send test">'
            + '<i class="las la-database"></i><i class="etb-data-dot" aria-hidden="true"></i> <span class="etb-btn-label etb-data-label"></span></button>');
        $('#etb-data-btn').on('click', EmailTemplateBuilder.openTestData);
        EmailTemplateBuilder.showTestRecord();
        // the builder's Preview form: add the test record on every submit
        $(document).off('submit.etb').on('submit.etb', '#cbuilder-preview', function () {
            $(this).find('input[name=etbRecordId], input[name=etbVariables]').remove();
            if (EmailTemplateBuilder.testRecordId) {
                $(this).append($('<input type="hidden" name="etbRecordId">').val(EmailTemplateBuilder.testRecordId));
            }
            $(this).append($('<input type="hidden" name="etbVariables">').val(JSON.stringify(EmailTemplateBuilder.testVariables || {})));
        });
    },

    showTestRecord: function () {
        var id = EmailTemplateBuilder.testRecordId;
        var values = $.grep($.map(EmailTemplateBuilder.testVariables || {}, function (v) { return v; }), function (v) { return v !== ''; }).length;
        var label = id ? 'Test data: ' + (id.length > 12 ? id.substring(0, 10) + '…' : id) : values ? 'Test data: ' + values + ' value' + (values > 1 ? 's' : '') : 'Test data';
        $('#etb-data-btn .etb-data-label').text(label);
        // a dot, not Bootstrap's .active: the builder theme styles an active toolbar button as a faint pill
        $('#etb-data-btn').toggleClass('etb-has-data', !!id || values > 0)
            .attr('title', 'Test data for Preview, Send test and the size meter: a form record for #form...# fields'
                + ' and test values for the template variables ({var...}). Kept in this browser; real sends are not affected.');
    },

    /** Templates saved before test values moved to the browser kept a "previewValue" per variable: start from it. */
    adoptPreviewValues: function () {
        if (!$.isEmptyObject(EmailTemplateBuilder.testVariables || {})) {
            return;
        }
        EmailTemplateBuilder.testVariables = EmailTemplateBuilder.testVariables || {};
        $.each(EmailTemplateBuilder.declaredVariables(), function (i, v) {
            if (v.previewValue) {
                EmailTemplateBuilder.testVariables[v.name] = v.previewValue;
            }
        });
        EmailTemplateBuilder.showTestRecord();
    },

    testVariablesKey: function () {
        return 'etb.testVariables.' + CustomBuilder.appId + '.' + CustomBuilder.id;
    },

    /** The template's variables from Settings: [{name, description, previewValue (older templates)}] */
    declaredVariables: function () {
        var rows = ((CustomBuilder.data && CustomBuilder.data.properties) || {}).variables || [];
        return $.grep(rows, function (v) { return v && /^[A-Za-z][A-Za-z0-9_]{0,63}$/.test($.trim(v.name || '')); });
    },

    /* Test data dialog: the form record ID and a test value per template variable, kept in this browser */
    openTestData: function () {
        var esc = function (v) { return $('<div>').text(v == null ? '' : v).html(); };
        $('#etb-td').remove();
        if ($('#etb-td-style').length === 0) {
            $('head').append('<style id="etb-td-style">'
                + '#etb-td{position:fixed;inset:0;z-index:20000;background:rgba(15,23,42,.55);display:flex;align-items:center;justify-content:center;padding:16px;}'
                + '#etb-td .etb-td-panel{background:#fff;border-radius:8px;width:100%;max-width:560px;max-height:calc(100vh - 32px);display:flex;flex-direction:column;box-shadow:0 10px 40px rgba(0,0,0,.3);color:#111827;font-size:14px;}'
                + '#etb-td .etb-td-head{display:flex;justify-content:space-between;align-items:center;padding:12px 16px;border-bottom:1px solid #e5e7eb;font-size:16px;}'
                + '#etb-td .etb-td-close{border:0;background:none;font-size:24px;line-height:1;cursor:pointer;color:#6b7280;}'
                + '#etb-td .etb-td-body{padding:12px 16px;overflow-y:auto;}'
                + '#etb-td .etb-td-note{color:#6b7280;font-size:12px;margin:0 0 12px;}'
                + '#etb-td label{display:block;font-weight:bold;margin:10px 0 4px;}'
                + '#etb-td label small{font-weight:normal;color:#6b7280;margin-left:6px;}'
                + '#etb-td input{width:100%;box-sizing:border-box;border:1px solid #d1d5db;border-radius:6px;padding:6px 8px;font-size:14px;}'
                + '#etb-td .etb-td-empty{color:#6b7280;font-size:13px;margin:4px 0;}'
                + '#etb-td .etb-td-foot{display:flex;justify-content:flex-end;gap:8px;padding:12px 16px;border-top:1px solid #e5e7eb;}'
                + '#etb-td .etb-td-foot .etb-td-clear{margin-right:auto;}'
                + '</style>');
        }
        var vars = EmailTemplateBuilder.declaredVariables();
        var rows = '';
        $.each(vars, function (i, v) {
            rows += '<label for="etb-td-v' + i + '">{var.' + esc(v.name) + '}' + (v.description ? '<small>' + esc(v.description) + '</small>' : '') + '</label>'
                + '<input type="text" id="etb-td-v' + i + '" data-name="' + esc(v.name) + '" value="' + esc((EmailTemplateBuilder.testVariables || {})[v.name] || '') + '">';
        });
        var dialog = $('<div id="etb-td" role="dialog" aria-modal="true" aria-labelledby="etb-td-title"><div class="etb-td-panel">'
            + '<div class="etb-td-head"><strong id="etb-td-title">Test data</strong><button type="button" class="etb-td-close" aria-label="Close">&times;</button></div>'
            + '<div class="etb-td-body"><p class="etb-td-note">Used only by Preview, Send test and the size meter, and kept in this browser for this template.'
            + ' Real sends are not affected.</p>'
            + '<label for="etb-td-record">Form record ID<small>fills #form.table.column#</small></label>'
            + '<input type="text" id="etb-td-record" placeholder="e.g. 8f3a2c1e-…" value="' + esc(EmailTemplateBuilder.testRecordId || '') + '">'
            + (vars.length ? '<div style="margin-top:16px;font-weight:bold;">Template variables</div>' + rows
                : '<div style="margin-top:16px;font-weight:bold;">Template variables</div><p class="etb-td-empty">This template has no variables. Add them in Settings (Template variables).</p>')
            + '</div><div class="etb-td-foot"><button type="button" class="btn btn-light etb-td-clear">Clear all</button>'
            + '<button type="button" class="btn btn-light etb-td-cancel">Cancel</button><button type="button" class="btn btn-primary etb-td-use">Use</button></div>'
            + '</div></div>').appendTo('body');
        var close = function () { dialog.remove(); $(document).off('keydown.etbtd'); };
        dialog.on('click', function (e) { if (e.target === this) { close(); } });
        dialog.find('.etb-td-close, .etb-td-cancel').on('click', close);
        $(document).on('keydown.etbtd', function (e) { if (e.key === 'Escape') { close(); } });
        dialog.find('.etb-td-clear').on('click', function () { dialog.find('input').val(''); });
        dialog.find('.etb-td-use').on('click', function () {
            var record = $.trim(dialog.find('#etb-td-record').val());
            if (record !== '' && !/^[A-Za-z0-9_\-.:@\/]{1,255}$/.test(record)) {
                alert('That is not a record ID. Copy the "id" of a form record, e.g. from a datalist or the form data. '
                    + 'Letters, digits and _ - . : @ / only.');
                return;
            }
            var values = {};
            dialog.find('input[data-name]').each(function () {
                values[$(this).data('name')] = $(this).val();
            });
            EmailTemplateBuilder.useTestData(record, values);
            close();
        });
        dialog.find('#etb-td-record').trigger('focus');
    },

    useTestData: function (record, values) {
        EmailTemplateBuilder.testRecordId = record;
        EmailTemplateBuilder.testVariables = values;
        try {
            if (record) {
                window.localStorage.setItem(EmailTemplateBuilder.testRecordKey(), record);
            } else {
                window.localStorage.removeItem(EmailTemplateBuilder.testRecordKey());
            }
            window.localStorage.setItem(EmailTemplateBuilder.testVariablesKey(), JSON.stringify(values));
        } catch (e) {
            // private window or blocked storage: keep it for this page only
        }
        EmailTemplateBuilder.showTestRecord();
        EmailTemplateBuilder.updateSize();
        EmailTemplateBuilder.refreshRepeatRows();
        if ($('#preview-iframe').is(':visible')) {
            $('#cbuilder-preview').submit();
        }
    },

    /* "Send test": the current design to the address(es) typed in the prompt, through the app's Email Tool SMTP defaults */
    sendTest: function () {
        var btn = $('#etb-test-btn');
        if (btn.prop('disabled')) {
            return;
        }
        var initial = EmailTemplateBuilder.lastTestTo || EmailTemplateBuilder.userEmail || '';
        var ask = window.UI && UI.prompt ? function (callback) {
            UI.prompt('Send the current design, with merge fields filled as in Preview'
                    + (EmailTemplateBuilder.testRecordId ? ' (test record ' + EmailTemplateBuilder.testRecordId + ')' : '')
                    + '. Separate several addresses with ";".', callback, {
                title: 'Send test email', inputType: 'text', inputValue: initial, inputPlaceholder: 'name@example.com', confirmButtonLabel: 'Send'
            });
        } : function (callback) {
            var v = window.prompt('Send a test of the current design to:', initial);
            if (v !== null) {
                callback(v);
            }
        };
        ask(function (to) {
            to = $.trim(to || '');
            if (to === '') {
                return;
            }
            EmailTemplateBuilder.lastTestTo = to;
            EmailTemplateBuilder.doSendTest(btn, to);
        });
    },

    doSendTest: function (btn, to) {
        var text = function (s) { return $('<div>').text(s).html(); };
        var notify = EmailTemplateBuilder.toast;
        btn.prop('disabled', true).find('i').attr('class', 'las la-spinner la-spin');
        $.ajax({
            type: 'POST',
            url: EmailTemplateBuilder.serviceUrl('sendTest'),
            data: {json: JSON.encode(CustomBuilder.data), to: to, etbRecordId: EmailTemplateBuilder.testRecordId || '',
                etbVariables: JSON.stringify(EmailTemplateBuilder.testVariables || {})},
            dataType: 'json',
            beforeSend: function (request) {
                request.setRequestHeader(ConnectionManager.tokenName, ConnectionManager.tokenValue);
            },
            success: function (r) {
                if (r.ok) {
                    var msg = 'Test email sent to ' + text(r.to) + '.';
                    if (r.warnings && r.warnings.length) {
                        msg += '<br>Check: ' + r.warnings.map(text).join('<br>');
                    }
                    notify(msg, r.warnings && r.warnings.length ? 8000 : 4000);
                } else {
                    alert('Test email not sent.\n\n' + r.error);
                }
            },
            error: function (xhr) {
                alert('Test email not sent (HTTP ' + xhr.status + ').');
            },
            complete: function () {
                btn.prop('disabled', false).find('i').attr('class', 'las la-paper-plane');
            }
        });
    },

    /*
     * ---------- Repeat: the selected datalist's fields as {row.NAME} buttons (click to copy) ----------
     * Saves nothing; it follows the block's Datalist field.
     */
    registerRowFieldsField: function () {
        if (typeof PropertyEditor === 'undefined' || PropertyEditor.Util.types['etbrowfields'] !== undefined) {
            return;
        }
        var type = function () {};
        type.prototype = {
            shortname: 'etbrowfields',
            renderField: function () {
                return '<div class="etb-rowfields" id="' + this.id + '"><span class="etb-rowfields-hint">Choose a datalist to see its fields.</span></div>';
            },
            getData: function () {
                return {};
            },
            initScripting: function () {
                var box = $('#' + this.id);
                // the block's Datalist field, in the same property panel (ids are editor-prefixed: <prefix>_listId)
                var list = $('[name="' + this.id.replace(/fields$/, 'listId') + '"]');
                if (list.length === 0) {
                    list = box.closest('.property-editor-container, .property-editor-page, form').find('select[name$="listId"]').first();
                }
                var esc = function (v) { return $('<div>').text(v).html(); };
                var load = function () {
                    var listId = list.val();
                    if (!listId) {
                        box.html('<span class="etb-rowfields-hint">Choose a datalist to see its fields.</span>');
                        return;
                    }
                    $.getJSON(EmailTemplateBuilder.serviceUrl('rowFields') + '&listId=' + encodeURIComponent(listId), function (fields) {
                        var html = '<span class="etb-rowfields-hint">Click a field to copy it, then paste it into a block inside the Repeat.</span>';
                        $.each(fields, function (i, f) {
                            html += '<button type="button" class="etb-rowfield" data-token="{row.' + esc(f.name) + '}">'
                                + '<code>{row.' + esc(f.name) + '}</code> <span>' + esc(f.label) + '</span></button>';
                        });
                        html += '<span class="etb-rowfields-hint">Add <code>?html</code> to keep a column\'s formatting (lists, badges), e.g. '
                            + '<code>{row.NAME?html}</code>, or <code>?raw</code> for the stored value.</span>';
                        box.html(fields.length ? html : '<span class="etb-rowfields-hint">This datalist has no columns.</span>');
                    });
                };
                box.on('click', '.etb-rowfield', function () {
                    var token = $(this).data('token');
                    var done = function () { EmailTemplateBuilder.toast('Copied <code>' + esc(token) + '</code>. Paste it into a Text or Button inside the Repeat.', 3000); };
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(token).then(done, function () { window.prompt('Copy this field:', token); });
                    } else {
                        window.prompt('Copy this field:', token);
                    }
                });
                list.on('change', load);
                load();
            }
        };
        PropertyEditor.Type.EtbRowFields = PropertyEditor.Util.inherit(PropertyEditor.Model.Type, type.prototype);
        $('head').append('<style id="etb-rowfields-style">'
            + '.etb-rowfields{display:flex;flex-wrap:wrap;gap:6px;align-items:center;}'
            + '.etb-rowfields-hint{flex-basis:100%;font-size:12px;color:#6b7280;line-height:1.4;}'
            + '.etb-rowfield{border:1px solid #d1d5db;border-radius:14px;background:#fff;padding:3px 10px;font-size:12px;cursor:pointer;}'
            + '.etb-rowfield:hover,.etb-rowfield:focus{border-color:#6e9f4b;outline:none;}'
            + '.etb-rowfield code{color:#0b5641;background:none;padding:0;font-size:12px;}'
            + '.etb-rowfield span{color:#6b7280;margin-left:2px;}'
            + '</style>');
    },

    /* ---------- colour field: swatch + hex + native picker (replaces the shared Coloris control) ---------- */

    registerColorField: function () {
        if (typeof PropertyEditor === 'undefined' || PropertyEditor.Util.types['etbcolor'] !== undefined) {
            return;
        }
        var HEX = /^#([0-9A-F]{3}|[0-9A-F]{6})$/;
        var SWATCHES = ['#FFFFFF', '#F4F4F4', '#EEEEEE', '#888888', '#333333', '#000000', '#1A73E8', '#0B5641', '#6E9F4B', '#C92A2A', '#E8590C', '#5F3DC4'];
        var full = function (v) {
            return v.length === 4 ? '#' + v[1] + v[1] + v[2] + v[2] + v[3] + v[3] : v;
        };
        var type = function () {};
        type.prototype = {
            shortname: 'etbcolor',
            renderField: function () {
                var v = (this.value === null || this.value === undefined) ? '' : String(this.value).trim().toUpperCase();
                var swatches = '';
                for (var i = 0; i < SWATCHES.length; i++) {
                    swatches += '<button type="button" class="etb-swatch" data-color="' + SWATCHES[i] + '" style="background:' + SWATCHES[i] + '" title="' + SWATCHES[i] + '"></button>';
                }
                return '<span class="etb-color' + (HEX.test(v) ? '' : ' etb-color-empty') + '">'
                    + '<label class="etb-color-chip" title="Pick a colour"><input type="color" class="etb-color-native" value="' + (HEX.test(v) ? full(v) : '#FFFFFF') + '"></label>'
                    + '<input type="text" class="etb-color-hex" id="' + this.id + '" name="' + this.id + '" value="' + PropertyEditor.Util.escapeHtmlTag(v)
                    + '" placeholder="none" maxlength="7" spellcheck="false" autocomplete="off">'
                    + '<button type="button" class="etb-color-clear" title="No colour (use the default)">&times;</button>'
                    + '</span><span class="etb-swatches">' + swatches + '</span>';
            },
            initScripting: function () {
                var hex = $('#' + this.id);
                var wrap = hex.closest('.etb-color');
                var native = wrap.find('.etb-color-native');
                var set = function (value, fire) {
                    value = (value || '').trim().toUpperCase();
                    hex.val(value);
                    wrap.toggleClass('etb-color-empty', !HEX.test(value));
                    if (HEX.test(value)) {
                        native.val(full(value));
                    }
                    if (fire) {
                        hex.trigger('change');
                    }
                };
                // the native picker is the browser's own: it tracks the pointer outside its box, no precise drag needed
                native.on('input', function () { set(this.value, false); });
                native.on('change', function () { set(this.value, true); });
                hex.on('input', function () {
                    var v = this.value.trim().toUpperCase();
                    if (v !== '' && v[0] !== '#') {
                        v = '#' + v;
                    }
                    wrap.toggleClass('etb-color-empty', !HEX.test(v));
                    if (HEX.test(v)) {
                        native.val(full(v));
                    }
                });
                hex.on('change', function () {
                    var v = this.value.trim().toUpperCase();
                    if (v !== '' && v[0] !== '#') {
                        v = '#' + v;
                    }
                    hex.val(HEX.test(v) || v === '' ? v : '');
                    wrap.toggleClass('etb-color-empty', !HEX.test(hex.val()));
                });
                wrap.find('.etb-color-clear').on('click', function () { set('', true); });
                wrap.next('.etb-swatches').find('.etb-swatch').on('click', function () { set($(this).data('color'), true); });
                PropertyEditor.Util.supportHashField(this);
            }
        };
        PropertyEditor.Type.EtbColor = PropertyEditor.Util.inherit(PropertyEditor.Model.Type, type.prototype);

        $('head').append('<style id="etb-color-style">'
            + '.etb-color{display:inline-flex;align-items:center;border:1px solid #d1d5db;border-radius:6px;background:#fff;height:34px;overflow:hidden;}'
            + '.etb-color-chip{position:relative;width:34px;height:34px;margin:0;border-right:1px solid #d1d5db;cursor:pointer;flex:none;}'
            + '.etb-color-native{position:absolute;inset:-6px;width:calc(100% + 12px);height:calc(100% + 12px);border:0;padding:0;cursor:pointer;background:none;}'
            + '.etb-color-empty .etb-color-native{opacity:0;}'
            + '.etb-color-empty .etb-color-chip{background:repeating-conic-gradient(#e5e7eb 0 25%,#fff 0 50%) 50%/12px 12px;}'
            + '.etb-color-hex{border:0 !important;outline:none;width:84px;padding:0 8px !important;font-family:monospace;font-size:13px;text-transform:uppercase;background:transparent;box-shadow:none !important;}'
            + '.etb-color-clear{border:0;background:none;color:#9ca3af;font-size:18px;line-height:1;padding:0 8px;cursor:pointer;}'
            + '.etb-color-clear:hover{color:#374151;}'
            + '.etb-swatches{display:flex;flex-wrap:wrap;gap:4px;margin-top:6px;}'
            // !important: the properties panel's button styles otherwise pad these into wide pills
            + '.etb-swatches .etb-swatch{width:18px !important;height:18px !important;min-width:0 !important;padding:0 !important;margin:0 !important;flex:none;'
            + 'border-radius:4px !important;border:1px solid rgba(0,0,0,.15) !important;cursor:pointer;box-shadow:none !important;}'
            + '.etb-swatch:hover{transform:scale(1.15);}'
            + '</style>');
    },

    /*
     * While a block is being inline-edited, the selection box keeps its blue tint and its label/action
     * bar ("Text ↑ ↓ 🗑"), which sits exactly where TinyMCE puts its toolbar. The framework has a
     * transparent ".text-edit" style for this but never applies it. Toggle a body class on the inline
     * editor's focus/blur: tint off, a thin dashed outline, and the action bar moved BELOW the block
     * (selecting a Text block always starts editing, so hiding it would lose move/delete).
     * Note: #cbuilder is the body's own id, so selectors hang off body.etb-inline-editing directly.
     */
    initInlineEditingChrome: function () {
        if ($('#etb-inline-style').length === 0) {
            $('head').append('<style id="etb-inline-style">'
                + 'body.etb-inline-editing #element-select-box{background:transparent !important;border:1px dashed rgba(66,133,244,.7) !important;}'
                + 'body.etb-inline-editing #element-select-box #element-select-name{top:100% !important;bottom:auto !important;border-radius:0 0 4px 4px !important;}'
                + 'body.etb-inline-editing #element-highlight-box{display:none !important;}'
                + '</style>');
        }
        var win = CustomBuilder.Builder.iframe && CustomBuilder.Builder.iframe.contentWindow;
        if (!win || !win.tinymce || EmailTemplateBuilder.inlineChromeHooked === win) {
            return;
        }
        EmailTemplateBuilder.inlineChromeHooked = win;
        var editing = function (on) {
            $('body').toggleClass('etb-inline-editing', on);
        };
        win.tinymce.on('AddEditor', function (e) {
            e.editor.on('focus', function () { editing(true); });
            e.editor.on('blur remove', function () { editing(false); });
        });
    }
};
