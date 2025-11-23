(function () {
    const STORAGE_KEY = '__recordedEvents';
    // 🔥 Debounce config for text input recording
    const TEXT_CHANGE_DEBOUNCE_MS = 700; // 1.5 sec idle time before recording
    const pendingTextChangeTimers = {};   // key -> { timeoutId, rec }

    let lsDirty = false;
    let lsFlushTimer = null;
    const LS_FLUSH_INTERVAL_MS = 1000;

    // ---- Load existing events from localStorage (if any) ----
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        window.__recordedEvents = saved ? JSON.parse(saved) : [];
        if (!Array.isArray(window.__recordedEvents)) {
            window.__recordedEvents = [];
        }
    } catch (e) {
        console.log('[recorder] Failed to read localStorage, starting fresh', e);
        window.__recordedEvents = [];
    }

    // Avoid double-installing listeners
    if (window.__recordingInstalled) {
        console.log('[recorder] Already installed on this page');
        return;
    }
    window.__recordingInstalled = true;

    window.__currentUrl = window.location.href;
    console.log('[recorder] Installed on', window.location.href);

    function scheduleLocalStorageFlush() {
        if (lsFlushTimer !== null) return; // already scheduled

        lsFlushTimer = setTimeout(function() {
            lsFlushTimer = null;
            if (!lsDirty) return;

            try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents || []));
            } catch (e) {
                console.log('[recorder] Failed to write to localStorage', e);
            }
            lsDirty = false;
        }, LS_FLUSH_INTERVAL_MS);
    }

    // ---- Central place to store and persist each event ----
    function persistEvent(rec) {
        (window.__recordedEvents || (window.__recordedEvents = [])).push(rec);

        // mark as dirty & schedule a flush
        lsDirty = true;
        scheduleLocalStorageFlush();

        // optional: comment this out if you want max speed
        // console.log('[recorder] Event stored:', rec.type, rec.action, rec.raw_gherkin);
    }

    // ---- Navigation logging ----
    function logNavigation(url) {
        if (!window.__recordedEvents) {
            window.__recordedEvents = [];
        }

        // 🔹 Global nav state (count) – initialize once
        if (!window.__recorderNavState) {
            window.__recorderNavState = { navCount: 0 };
        }

        if (!url || typeof url !== 'string' || url.length === 0) {
            console.warn('[recorder] Skipping navigation: URL is empty or invalid.');
            return;
        }
        const urlRegex = /^(http|https|file):\/\/[^\s$.?#].[^\s]*$/i;
        if (!urlRegex.test(url)) {
            console.warn('[recorder] Skipping navigation: URL does not look like an absolute URL.', url);
            return;
        }

        const escapedUrl = url.replace(/\"/g, '\\\"');
        const pageTitle =
            document.title && document.title.trim().length > 0
                ? document.title.trim()
                : '';

        // If title is empty, fall back to URL as the "page" label
        let pageName = pageTitle || url;
        pageName = pageName.trim().replace(/\"/g, '\\"');

        const isFirstNav = window.__recorderNavState.navCount === 0;
        window.__recorderNavState.navCount += 1;

        let stepText;
        let rawSelenium;

        if (isFirstNav) {
            // ✅ First navigation
            stepText = `I navigate to "${pageName}" page`;
            rawSelenium = 'driver.get(\"' + escapedUrl + '\");';
        } else {
            // ✅ Subsequent navigations
            stepText = `I am on "${pageName}" page`;
            rawSelenium = ''; // no driver.get for later navigations
        }

        const rec = {
            timestamp: Date.now(),
            type: 'navigation',
            action: 'navigate',
            url: url,
            title: pageTitle,
            raw_gherkin: stepText,
            raw_selenium: rawSelenium
        };

        persistEvent(rec);
        window.__currentUrl = url;
    }

    // Expose for manual debug if needed
    window.__logNavigation = logNavigation;

    // --- URL Change Detection (for initial load and SPAs) ---
    setTimeout(() => {
        logNavigation(window.location.href);
    }, 500); // small delay so title is set

    window.addEventListener('popstate', () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) {
                logNavigation(window.location.href);
            }
        }, 100);
    });

    window.addEventListener('hashchange', () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) {
                logNavigation(window.location.href);
            }
        }, 100);
    });

    (function (history) {
        const pushState = history.pushState;
        history.pushState = function () {
            const result = pushState.apply(history, arguments);
            setTimeout(() => {
                if (window.location.href !== window.__currentUrl) {
                    logNavigation(window.location.href);
                }
            }, 100);
            return result;
        };
    })(window.history);

    // ---- Helpers for click/change recording ----

    // ⭐ UPDATED: now walks ancestors, reads title/aria-label/SVG <title> etc.
    function getAccessibleName(t) {
        if (!t) return '';

        var original = t;

        // 1️⃣ Walk up ancestors and look for aria-label / title / img alt
        var el = t;
        while (el) {
            if (el.getAttribute) {
                var aria = el.getAttribute('aria-label');
                if (aria && aria.trim().length > 0) {
                    return aria.trim();
                }

                var titleAttr = el.getAttribute('title');
                if (titleAttr && titleAttr.trim().length > 0) {
                    return titleAttr.trim();
                }

                if (el.tagName && el.tagName.toLowerCase() === 'img') {
                    var alt = el.getAttribute('alt');
                    if (alt && alt.trim().length > 0) {
                        return alt.trim();
                    }
                }
            }
            el = el.parentElement;
        }

        // 2️⃣ <label for="id"> for the original element
        var id = original.id;
        if (id) {
            var labelEl = document.querySelector('label[for="' + id + '"]');
            if (labelEl) {
                return (labelEl.innerText || labelEl.textContent || '').trim();
            }
        }

        // 3️⃣ name attribute on the original element
        if (original.name) {
            return original.name.trim();
        }

        // 4️⃣ SVG <title> via nearest <svg> ancestor
        try {
            var svg = original.closest && original.closest('svg');
            if (svg) {
                var svgTitleEl = svg.querySelector('title');
                if (svgTitleEl && svgTitleEl.textContent && svgTitleEl.textContent.trim().length > 0) {
                    return svgTitleEl.textContent.trim();
                }
            }
        } catch (e) {
            // ignore
        }

        // 5️⃣ Last resort: any visible text on the element or its ancestors
        el = original;
        while (el) {
            var txt = (el.innerText || el.textContent || '').trim();
            if (txt.length > 0) {
                return txt;
            }
            el = el.parentElement;
        }

        return '';
    }

    function generateSeleniumLocator(t, accName) {
        var id = t.id;
        var name = t.name;
        var tag = t.tagName.toLowerCase();

        var elementText = t.innerText ? t.innerText.trim() : (t.textContent ? t.textContent.trim() : '');
        var normalizedText = elementText.replace(/'/g, "\\'");
        var normalizedAccName = accName.replace(/'/g, "\\'");

        // Special case: links → By.linkText when we have text
        if (tag === 'a' && elementText.length > 0) {
            return { type: 'By.linkText', value: elementText };
        }

        // PRIORITY 1: Element Text (XPath by Text) for non-input/textarea
        if (elementText.length > 0 && tag !== 'input' && tag !== 'textarea') {
            var xpath = "//" + tag + "[normalize-space(.)='" + normalizedText + "']";
            return { type: 'By.xpath', value: xpath };
        }

        // PRIORITY 2: By Name
        if (name && name.length > 0) {
            return { type: 'By.name', value: name };
        }

        // PRIORITY 3: By ID
        if (id && id.length > 0) {
            return { type: 'By.id', value: id };
        }

        // PRIORITY 4: Accessible name–based XPath
        if (accName.length > 0) {
            var xpath2 = "//label[normalize-space(.)='" + normalizedAccName + "']/following-sibling::" + tag +
                " | //" + tag + "[@aria-label='" + normalizedAccName + "']" +
                " | //" + tag + "[@placeholder='" + normalizedAccName + "']";
            return { type: 'By.xpath', value: xpath2 };
        }

        // Final Fallback: tag selector
        return { type: 'By.cssSelector', value: tag };
    }

    // --- Special handling for Select2 / dropdown options ---
    document.addEventListener('mouseup', function (e) {
        try {
            // Only care about actual Select2 option elements
            var optionEl = e.target.closest && e.target.closest('.select2-results__option');
            if (!optionEl) return; // Not a Select2 option → ignore

            var rec = {};
            rec.timestamp = Date.now();
            rec.type = 'click';              // treat as click in our model
            rec.title = document.title;

            // Get only the text of the option, not the whole list
            var elementText = optionEl.textContent ? optionEl.textContent.trim() : '';
            var accName = ''; // not really needed for Select2 options

            // Use your existing locator helper (it will build an XPath with the option text)
            var locator = generateSeleniumLocator(optionEl, accName);
            var locatorValue = locator.value.replace(/\"/g, '\\\"');

            rec.selector = 'option';

            var gherkinName = elementText;
            rec.options = {};
            if (elementText.length > 0) {
                rec.options.element_text = elementText;
                rec.options.primary_name = elementText;
            }

            rec.action = 'click';
            rec.raw_gherkin = 'I select the "' + gherkinName + '" option';
            rec.raw_selenium =
                'driver.findElement(' + locator.type + '(\"' + locatorValue + '\")).click();';

            persistEvent(rec);
        } catch (err) {
            console && console.log && console.log('[recorder] select2 option mouseup handler error', err);
        }
    }, true);

    document.addEventListener('click', function (event) {
        let t = event.target;

        // If click lands on a text node, go to its parent element
        if (t && t.nodeType === 3) {
            t = t.parentNode;
        }

        // 🔧 NEW: normalize to *clickable ancestor* if possible
        if (t && t.closest) {
            const clickable = t.closest('button, a, [role="button"], [onclick]');
            if (clickable) {
                t = clickable;
            }
        }

        const tag  = (t.tagName || '').toLowerCase();
        const role = (t.getAttribute && t.getAttribute('role')) || '';

        // 🔍 Optional debug for this SSO case
        // console.log('REC DEBUG CLICK:', { tag, role, id: t.id, cls: t.className });

        // ... from here down, use THIS `t`, `tag`, `role` ...
        // e.g. resolve locator based on `t`
        // const locator = resolveLocator(t);
        // const gherkinName = buildGherkinName(t, locator);

        switch (event.type) {
            // ⬇ your case 'click' goes here
        }
    });

    // ---- Main event recorder for click & change ----
    function recordEvent(e) {
        try {
            if (!e) return;

            // 🔇 Ignore script-generated CHANGE events (auto-fill, .trigger('change'), etc.)
            // but DO NOT block clicks here (we want Select2 option clicks).
            if (e.type === 'change' && e.isTrusted === false) {
                return;
            }

            var t = e.target;

            // If there's no usable target, bail out
            if (!t || t.nodeType !== 1) { // 1 = ELEMENT_NODE
                return;
            }

            // ⭐ NEW: For click events, promote inner SVG/ICON clicks
            //         to the closest clickable ancestor (button/link/etc.)
            if (e.type === 'click' && t.closest) {
                var clickableAncestor = t.closest('button, a, [role="button"], [role="link"], [onclick]');
                if (clickableAncestor) {
                    t = clickableAncestor;
                }
            }

            var rec = {};
            rec.timestamp = Date.now();
            rec.type = e.type;
            rec.title = document.title;

            var accName = getAccessibleName(t);
            var elementText = t.innerText ? t.innerText.trim() : (t.textContent ? t.textContent.trim() : '');

            // Role detection
            var role = t.getAttribute('role');
            const tag = t.tagName.toLowerCase();

            if (!role) {
                if (tag === 'input') {
                    if (t.type === 'checkbox') {
                        role = 'checkbox';
                    } else if (t.type === 'radio') {
                        role = 'radio';
                    } else if (t.type === 'submit' || t.type === 'button') {
                        role = 'button'; // treat submit as button
                    } else if (['text', 'password', 'email', 'search', 'number'].includes(t.type)) {
                        role = 'textbox';
                    } else {
                        role = t.type;
                    }
                } else if (tag === 'textarea') {
                    role = 'textbox';
                } else if (tag === 'a') {
                    role = 'link';
                } else {
                    role = tag;
                }
            }

            var gherkinRole = role;
            if (role === 'radio') gherkinRole = 'radio button';
            else if (role === 'checkbox') gherkinRole = 'checkbox button';
            else if (role === 'textbox') gherkinRole = 'textbox field';

            var locator = generateSeleniumLocator(t, accName);
            var locatorValue = locator.value.replace(/\"/g, '\\\"');

            rec.selector = role;

            // Gherkin naming priority
            // ✅ Gherkin naming priority (prefer label/aria-label over technical name)
            var gherkinName = '';
            if (elementText.length > 0) {
                // Visible text on the element (e.g. button text, link text)
                gherkinName = elementText;
            } else if (accName && accName.length > 0) {
                // Accessible name: aria-label, <label for>, title, SVG <title>, etc.
                gherkinName = accName;      // 👈 e.g. "Registry ID"
            } else if (t.value && t.type === 'submit') {
                // Submit button text
                gherkinName = t.value;      // e.g. "Sign in"
            } else if (t.name && t.name.length > 0) {
                // Technical name only if we have nothing better
                gherkinName = t.name;
            } else if (t.id && t.id.length > 0) {
                gherkinName = t.id;
                gherkinName = t.id;
            }

            rec.options = {};
            if (elementText.length > 0) rec.options.element_text = elementText;
            if (t.id) rec.options.id = t.id;
            if (t.name) rec.options.name = t.name;
            rec.options.primary_name = gherkinName;

            switch (e.type) {
               case 'click': {

                   // 🔧 Only skip true textboxes, not buttons/links mis-labeled as textbox
                   if (role === 'textbox' && tag !== 'button' && tag !== 'a') {
                       return;
                   }

                   rec.action = 'click';
                   rec.raw_selenium =
                       'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';

                   // ✅ Checkbox handling
                   if (t.type === 'checkbox' || role === 'checkbox') {
                       if (t.checked) {
                           rec.raw_gherkin = 'I check the "' + gherkinName + '" option';
                       } else {
                           rec.raw_gherkin = 'I uncheck the "' + gherkinName + '" option';
                       }
                       break;
                   }

                   // ✅ Dropdown / select-like widgets
                   const isSelectDropdown =
                       tag === 'select' ||                                     // native <select>
                       (t.closest && t.closest('.select2')) ||                 // Select2 wrapper
                       t.classList.contains('select2') ||
                       /select|dropdown/i.test(locatorValue) ||
                       t.getAttribute('role') === 'option' ||
                       t.getAttribute('aria-selected') !== null;

                   if (isSelectDropdown) {
                       rec.raw_gherkin = 'I select the "' + gherkinName + '" option';

                   } else if (role === 'link' || tag === 'a') {
                       // Links
                       rec.raw_gherkin = 'I click on the "' + gherkinName + '" link';

                   } else if (t.type === 'submit' || t.type === 'button' || role === 'button') {
                       // 🔧 Explicitly handle <button type="button"> (your SSO button)
                       rec.raw_gherkin = 'I click on the "' + gherkinName + '" button';

                   } else {
                       // Generic click fallback
                       rec.raw_gherkin = 'I click on the "' + gherkinName + '"';
                   }

                   break;
               }

                case 'change': {
                    // 🔒 Avoid duplicate events for checkbox / radio
                    if (t.type === 'checkbox' || t.type === 'radio') {
                        // We already record the click; ignore the change for these controls.
                        return;
                    }

                    var value = t.value !== undefined ? t.value : null;

                    rec.action = 'sendKeys';
                    rec.parsedValue = value;
                    rec.value = value;

                    rec.raw_selenium =
                        'driver.findElement(' + locator.type + '(\"' + locatorValue + '\")).sendKeys(\"' + value + '\");';

                    rec.raw_gherkin = 'I enter "' + value + '" into the "' + gherkinName + '"';

                    rec.options.value = value;
                    rec.options.parsedValue = value;

                    // 🔄 NEW: debounce for textboxes so we don't record partial values
                    if (role === 'textbox') {
                        // Identify this field (prefer id, then name, then locator)
                        const fieldKey =
                            (t.id && ('id:' + t.id)) ||
                            (t.name && ('name:' + t.name)) ||
                            ('loc:' + locator.value);

                        // Clear any pending timer for this field
                        const existing = pendingTextChangeTimers[fieldKey];
                        if (existing && existing.timeoutId) {
                            clearTimeout(existing.timeoutId);
                        }

                        // Schedule a new timer; only the last value gets recorded
                        const timeoutId = setTimeout(function () {
                            persistEvent(rec);
                            delete pendingTextChangeTimers[fieldKey];
                        }, TEXT_CHANGE_DEBOUNCE_MS);

                        pendingTextChangeTimers[fieldKey] = { timeoutId, rec };
                    } else {
                        // Non-textbox (e.g. selects) → record immediately
                        persistEvent(rec);
                    }
                }
                default:
                    return;
            }

            if (!gherkinName || gherkinName.length === 0) {
                rec.raw_gherkin = 'I interact with the unlabeled ' + gherkinRole + ' element';
            }

            persistEvent(rec);
        } catch (err) {
            console && console.log && console.log('[recorder] recordEvent error', err);
        }
    }

    ['click', 'change'].forEach(function (type) {
        window.addEventListener(type, recordEvent, true);
    });

    // ❌ No submit recording – we only care about click

})();
