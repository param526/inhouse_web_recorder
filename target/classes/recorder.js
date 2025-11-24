(function () {
    const STORAGE_KEY = '__recordedEvents';
    // 🔥 Debounce config for text input recording
    const TEXT_CHANGE_DEBOUNCE_MS = 700;
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

            // Build the navigation record FIRST
            const navRec = {
                timestamp: Date.now(),
                type: 'navigation',
                action: 'navigate',
                url: url,
                title: pageTitle,
                raw_gherkin: stepText,
                raw_selenium: rawSelenium
            };

            // 👇 We may add a synthetic SSO click AFTER this nav
            let syntheticSsoClick = null;

            try {
                // 💡 Oracle SSO special case: obrareq.cgi redirect
                if (/\/oam\/server\/obrareq\.cgi/i.test(url)) {
                    var now = Date.now();

                    // Avoid spamming duplicates
                    if (!window.__lastSsoSyntheticClickTs ||
                        now - window.__lastSsoSyntheticClickTs > 3000) {

                        var ssoBtn = document.getElementById('ssoBtn');
                        if (ssoBtn && window.__recordingInstalled) {

                            var accName = getAccessibleName(ssoBtn);
                            var elementText = (ssoBtn.innerText || ssoBtn.textContent || '').trim();

                            var gherkinName =
                                elementText ||
                                accName ||
                                ssoBtn.id ||
                                'Company Single Sign-On';

                            var locator = generateSeleniumLocator(ssoBtn, accName);
                            var locatorValue = locator.value.replace(/\"/g, '\\\"');

                            syntheticSsoClick = {
                                timestamp: now,
                                type: 'click',
                                title: document.title,
                                action: 'click',
                                selector: 'button',
                                raw_gherkin: 'I click on the "' + gherkinName + '" button',
                                raw_selenium:
                                    'driver.findElement(' +
                                    locator.type + '("' + locatorValue + '")).click();',
                                options: {
                                    id: ssoBtn.id,
                                    name: ssoBtn.name || '',
                                    element_text: elementText,
                                    primary_name: gherkinName
                                }
                            };

                            window.__lastSsoSyntheticClickTs = now;
                        }
                    }
                }
            } catch (e) {
                console && console.log &&
                    console.log('[recorder] SSO synthetic click prepare error', e);
            }

            // 1️⃣ Persist the navigation FIRST
            persistEvent(navRec);
            window.__currentUrl = url;

            // 2️⃣ Then (if we built it) persist the synthetic SSO click AFTER
            if (syntheticSsoClick) {
                persistEvent(syntheticSsoClick);

                try {
                    // Immediate flush, since SSO redirects fast
                    localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents || []));
                } catch (e) {
                    console && console.log &&
                        console.log('[recorder] SSO synthetic click flush failed', e);
                }

                console && console.log &&
                    console.log('[recorder] Synthetic SSO click recorded AFTER navigation');
            }
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

    window.addEventListener('beforeunload', function () {
        try {
            if (window.__recordedEvents) {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents));
            }
        } catch (e) {
            console.log('[recorder] beforeunload flush failed', e);
        }
    });

    // --- Dedicated handler for Select2 / dropdown options --

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
        if (!t) {
            return { type: 'By.cssSelector', value: '*' };
        }

        var id   = t.id || '';
        var name = t.name || '';
        var tag  = (t.tagName || '').toLowerCase();

        var elementText       = (t.innerText || t.textContent || '').trim();
        var normalizedText    = elementText.replace(/'/g, "\\'");
        var normalizedAccName = (accName || '').replace(/'/g, "\\'");

        // 🔹 Special case: links → By.linkText when we have text
        if (tag === 'a' && elementText.length > 0) {
            return { type: 'By.linkText', value: elementText };
        }

        // 🔹 PRIORITY 1: Element Text (XPath by Text) for non-input/textarea
        if (elementText.length > 0 && tag !== 'input' && tag !== 'textarea') {
            var xpath = "//" + tag + "[normalize-space(.)='" + normalizedText + "']";
            return { type: 'By.xpath', value: xpath };
        }

        // 🔹 PRIORITY 2: By Name
        if (name && name.length > 0) {
            return { type: 'By.name', value: name };
        }

        // 🔹 PRIORITY 3: By ID
        if (id && id.length > 0) {
            return { type: 'By.id', value: id };
        }

        // 🔹 PRIORITY 4: Accessible name–based XPath
        if (normalizedAccName.length > 0) {
            var xpath2 =
                "//label[normalize-space(.)='" + normalizedAccName + "']/following-sibling::" + tag +
                " | //" + tag + "[@aria-label='" + normalizedAccName + "']" +
                " | //" + tag + "[@placeholder='" + normalizedAccName + "']";
            return { type: 'By.xpath', value: xpath2 };
        }

        // 🔹 Final Fallback: tag selector
        return { type: 'By.cssSelector', value: tag || '*' };
    }


    // --- Dedicated handler for dropdown options (Select2 & friends) ---
    function handleDropdownOptionEvent(e) {
        try {
            if (!e || !e.target || !e.target.closest) return;

            // Cover Select2 + generic ARIA options
            const optionEl = e.target.closest(
                '.select2-results__option,' +
                'li[role="option"],' +
                '[role="option"],' +
                'li[aria-selected]'
            );

            if (!optionEl) return;  // not a dropdown option → ignore

            const optionText = (optionEl.innerText || optionEl.textContent || '').trim();
            if (!optionText) return;

            const rec = {};
            rec.timestamp = Date.now();
            rec.type = 'click';
            rec.title = document.title;
            rec.action = 'click';

            // 🔒 Build a stable locator for this option
            const escapedText = optionText.replace(/'/g, "\\'");

            let locatorExpr;

            // Select2-style option
            if (optionEl.classList.contains('select2-results__option')) {
                locatorExpr =
                    'By.xpath("//li[contains(@class,\'select2-results__option\') and normalize-space()=\'' +
                    escapedText + '\']")';
            }
            // Generic ARIA role="option"
            else if (optionEl.getAttribute('role') === 'option' ||
                     optionEl.getAttribute('aria-selected') != null) {
                locatorExpr =
                    'By.xpath("(//*[@role=\'option\' and normalize-space()=\'' +
                    escapedText + '\'])[1]")';
            }
            // Fallback – any element with that text
            else {
                locatorExpr =
                    'By.xpath("(//*[normalize-space()=\'' + escapedText + '\'])[1]")';
            }

            rec.raw_selenium =
                'driver.findElement(' + locatorExpr + ').click();';

            rec.raw_gherkin = 'I select "' + optionText + '" from the dropdown';

            rec.selector = 'option';
            rec.options = {
                element_text: optionText,
                primary_name: optionText
            };

            persistEvent(rec);
        } catch (err) {
            console && console.log && console.log('[recorder] dropdown option handler error', err);
        }
    }

    // Attach to BOTH mouseup + click so we catch whatever the widget uses
    document.addEventListener('mouseup', handleDropdownOptionEvent, true);
    document.addEventListener('click', handleDropdownOptionEvent, true);


    // ---- Main event recorder for click & change ----
    function recordEvent(e) {
        try {
            if (!e) return;

            // 🔇 Ignore script-generated events (we only want real user actions)
                    if (!e.isTrusted) {
                        return;
                    }

            var t = e.target;

            // If there's no usable target, bail out
            if (!t || t.nodeType !== 1) { // 1 = ELEMENT_NODE
                return;
            }

            // ⭐ For click events, normalize to the *real* clickable element.
            // This makes mega-menu tiles like "New Order" work even if the click
            // lands on the <li> background, icon <i>, or nested <span>.
            if (e.type === 'click' && t.closest) {
                // 1️⃣ Prefer an ancestor that is inherently clickable
                let clickable = t.closest('a[href], button, [role="button"], [role="link"], [onclick]');

                // 2️⃣ If the event target is a container (LI/DIV), look *inside* it
                //    for the first inner link/button widget (mega-menu tiles, cards, etc.).
                if (!clickable && (t.tagName === 'LI' || t.tagName === 'DIV') && t.querySelector) {
                    clickable = t.querySelector('a[href], button, [role="button"], [role="link"]');
                }

                if (clickable) {
                    t = clickable;
                }
            }


            var rec = {};
            rec.timestamp = Date.now();
            rec.type = e.type;
            rec.title = document.title;

            var accName = getAccessibleName(t);
            var elementText = t.innerText ? t.innerText.trim() :
                              (t.textContent ? t.textContent.trim() : '');

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
                        role = 'button';
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
            var gherkinName = '';
            if (elementText.length > 0) {
                gherkinName = elementText;
            } else if (accName && accName.length > 0) {
                gherkinName = accName;
            } else if (t.value && t.type === 'submit') {
                gherkinName = t.value;
            } else if (t.name && t.name.length > 0) {
                gherkinName = t.name;
            } else if (t.id && t.id.length > 0) {
                gherkinName = t.id;
            }

            rec.options = {};
            if (elementText.length > 0) rec.options.element_text = elementText;
            if (t.id) rec.options.id = t.id;
            if (t.name) rec.options.name = t.name;
            rec.options.primary_name = gherkinName;

            switch (e.type) {

                // ================= CLICK HANDLING =================
                case 'click': {
                    // Normalize basics
                    const tagName = (t.tagName || '').toLowerCase();
                    const roleAttr = (t.getAttribute && t.getAttribute('role')) || '';
                    const roleLower = roleAttr.toLowerCase();
                    const classList = Array.from(t.classList || []);

                    // Is this click somewhere inside a dropdown widget?
                    const dropdownContainer =
                        t.closest && t.closest(
                            [
                                '.select2-container',
                                '.select2',
                                '[role="combobox"]',
                                '[aria-haspopup="listbox"]',
                                '.oj-select-choice',
                                '.oj-combobox-choice',
                                '.ui-selectmenu-button',
                                '.dropdown-toggle'
                            ].join(',')
                        );
                    const insideDropdown = !!dropdownContainer;

                    // Is this an option inside a dropdown?
                    const optionEl =
                        t.closest && t.closest(
                            [
                                '.select2-results__option',
                                'option',
                                'li[role="option"]',
                                '[role="option"]',
                                'li[aria-selected]',
                                '.ui-menu-item',
                                '.oj-listbox-result'
                            ].join(',')
                        );
                    const isDropdownOption = !!optionEl;

                    // Consider only *real* textboxes as ignorable
                    const isRealTextbox =
                        (tagName === 'input' && [
                            'text', 'search', 'email', 'password', 'tel', 'number', 'url'
                        ].includes(t.type)) ||
                        tagName === 'textarea' ||
                        t.isContentEditable === true;

                    // 🔧 Skip real text inputs, but NOT fake dropdown "textbox" spans
                    if (isRealTextbox && roleLower === 'textbox' && !insideDropdown) {
                        return;
                    }

                    // 🔎 Ignore big container DIV clicks (layout, info panels)
                    const textForHeuristic = elementText || '';
                    const looksLikeContainerDiv =
                        tagName === 'div' &&
                        !isDropdownOption &&
                        !insideDropdown &&
                        !t.hasAttribute('onclick') &&
                        !t.getAttribute('role') &&
                        !t.isContentEditable;

                    if (looksLikeContainerDiv) {
                        const hasNewlines = textForHeuristic.indexOf('\n') !== -1;
                        const isVeryLong  = textForHeuristic.length > 80;
                        if (isVeryLong && hasNewlines) {
                            // ✅ treat as non-interactive layout click
                            return;
                        }
                    }

                    // ----- Determine whether this element is "actionable" -----
                    const isNativeSelect = tagName === 'select';
                    const looksLikeDropdownName =
                        /select|dropdown/i.test(locatorValue || '');

                    const isDropdownActivator =
                        isNativeSelect ||
                        insideDropdown ||
                        roleLower === 'combobox' ||
                        looksLikeDropdownName;

                    const isCheckboxLike =
                        t.type === 'checkbox' || roleLower === 'checkbox';

                    const isButtonLike =
                        t.type === 'submit' ||
                        t.type === 'button' ||
                        roleLower === 'button';

                    const isLinkLike =
                        roleLower === 'link' || tagName === 'a';

                    const hasOnclick =
                        !!t.getAttribute('onclick') || typeof t.onclick === 'function';

                    // Stimulus / data-action click handlers
                    const hasDataActionClick =
                        !!(t.closest && t.closest('[data-action*="click->"]'));

                    const isActionable =
                        isDropdownActivator ||
                        isDropdownOption ||
                        isCheckboxLike ||
                        isButtonLike ||
                        isLinkLike ||
                        hasOnclick ||
                        hasDataActionClick;

                    // ❌ Ignore generic non-actionable layout clicks
                    if (!isActionable) {
                        return;
                    }

                    rec.action = 'click';

                    // ========= SPECIAL CASE: icon-only <a> like menu icons =========
                    const textContent = (t.textContent || '').trim();
                    const mainClass =
                        classList.find(c => !c.startsWith('-')) || classList[0] || null;

                    if (tagName === 'a' && !textContent && mainClass) {
                        const css = 'a.' + mainClass.split(/\s+/).join('.');

                        rec.raw_selenium =
                            'driver.findElement(By.cssSelector("' + css + '")).click();';

                        const niceName = mainClass
                            .replace(/[-_]+/g, ' ')
                            .replace(/\s+/g, ' ')
                            .trim()
                            .replace(/^./, c => c.toUpperCase());

                        rec.raw_gherkin = 'I click on the "' + niceName + '" link';
                        rec.options.primary_name = niceName;
                        break;
                    }

                    // ---------- 1) OPTION CLICK (Select2 / dropdowns) ----------
                    if (isDropdownOption) {
                        const opt = optionEl;
                        const rawOptionText = (opt.innerText || opt.textContent || '').trim();
                        if (!rawOptionText) return;

                        // Use only the first line as the key (some options have multi-line details)
                        const firstLine = rawOptionText.split(/\r?\n/)[0].trim();
                        const escapedFirstLine = firstLine.replace(/'/g, "\\'");

                        let dropdownName = accName;
                        if (!dropdownName && dropdownContainer) {
                            dropdownName = getAccessibleName(dropdownContainer);
                        }
                        if (!dropdownName) {
                            dropdownName = (gherkinName && gherkinName.length > 0)
                                ? gherkinName
                                : 'dropdown';
                        }

                        let locatorExpr;
                        if (opt.classList && opt.classList.contains('select2-results__option')) {
                            locatorExpr =
                                "By.xpath(\"//li[contains(@class,'select2-results__option') " +
                                "and contains(normalize-space(.), '" + escapedFirstLine + "')]\")";
                        } else if (opt.tagName && opt.tagName.toLowerCase() === 'option') {
                            locatorExpr =
                                "By.xpath(\"//option[contains(normalize-space(.), '" + escapedFirstLine + "')]\")";
                        } else {
                            locatorExpr =
                                "By.xpath(\"(//*[contains(normalize-space(.), '" + escapedFirstLine + "')])[1]\")";
                        }

                        rec.raw_gherkin =
                            'I select "' + firstLine + '" from the "' + dropdownName + '" dropdown';

                        rec.raw_selenium =
                            'driver.findElement(' + locatorExpr + ').click();';

                        rec.options.element_text = rawOptionText;   // full text in JSON
                        rec.options.primary_name = firstLine;       // nice display name

                        break;
                    }

                    // ---------- 2) DROPDOWN "OPEN" CLICK ----------
                    if (isDropdownActivator) {
                        let dropdownLabel = accName || gherkinName || 'dropdown';

                        rec.raw_gherkin = 'I open the "' + dropdownLabel + '" dropdown';

                        if (insideDropdown && dropdownContainer) {
                            const selectionEl =
                                dropdownContainer.querySelector('.select2-selection') ||
                                dropdownContainer.querySelector('.oj-select-choice') ||
                                dropdownContainer.querySelector('.oj-combobox-choice') ||
                                dropdownContainer;

                            let byExpr;

                            if (selectionEl.id) {
                                byExpr = 'By.id("' + selectionEl.id + '")';
                            } else {
                                const ariaLabelledby = selectionEl.getAttribute('aria-labelledby');
                                const containerId    = dropdownContainer.id;
                                const dataSelect2Id  = dropdownContainer.getAttribute('data-select2-id');

                                if (ariaLabelledby) {
                                    byExpr =
                                        'By.cssSelector(".select2-selection[aria-labelledby=\'' +
                                        ariaLabelledby.replace(/'/g, "\\'") +
                                        '\']")';
                                } else if (containerId) {
                                    byExpr =
                                        'By.cssSelector("#' +
                                        containerId.replace(/'/g, "\\'") +
                                        ' .select2-selection")';
                                } else if (dataSelect2Id) {
                                    byExpr =
                                        'By.cssSelector(".select2-container[data-select2-id=\'' +
                                        dataSelect2Id.replace(/'/g, "\\'") +
                                        '\'] .select2-selection")';
                                } else {
                                    const allSelections = Array.from(
                                        document.querySelectorAll('.select2-container .select2-selection')
                                    );
                                    const idx = allSelections.indexOf(selectionEl);
                                    if (idx >= 0) {
                                        const nth = idx + 1;
                                        byExpr =
                                            'By.cssSelector(".select2-container .select2-selection:nth-of-type(' +
                                            nth +
                                            ')")';
                                    } else {
                                        byExpr = locator.type + '("' + locatorValue + '")';
                                    }
                                }
                            }

                            rec.raw_selenium =
                                'driver.findElement(' + byExpr + ').click();';

                        } else if (isNativeSelect) {
                            rec.raw_selenium =
                                'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';
                        } else {
                            rec.raw_selenium =
                                'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';
                        }

                        break;
                    }

                    // ---------- 3) CHECKBOXES ----------
                    if (t.type === 'checkbox' || roleLower === 'checkbox') {
                        rec.raw_gherkin = t.checked
                            ? 'I check the "' + gherkinName + '" option'
                            : 'I uncheck the "' + gherkinName + '" option';

                        rec.raw_selenium =
                            'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';
                        break;
                    }

                    // ---------- 4) LINKS ----------
                    if (isLinkLike) {
                        const linkText = (t.innerText || t.textContent || '').trim();
                        const escaped = linkText.replace(/\"/g, '\\\"');

                        rec.raw_gherkin = 'I click on the "' + linkText + '" link';
                        rec.raw_selenium =
                            'driver.findElement(By.linkText(\"' + escaped + '\")).click();';

                        rec.options.primary_name = linkText;
                        break;
                    }

                    // ---------- 5) BUTTONS ----------
                    if (isButtonLike) {
                        rec.raw_gherkin = 'I click on the "' + gherkinName + '" button';
                        rec.raw_selenium =
                            'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';
                        break;
                    }

                    // ---------- 6) GENERIC CLICK (custom widgets with onclick / data-action) ----------
                    rec.raw_gherkin = 'I click on the "' + gherkinName + '"';
                    rec.raw_selenium =
                        'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';

                    break;
                }

                // ================= CHANGE HANDLING =================
                case 'change': {
                    // 🔒 Avoid duplicate events for checkbox / radio
                    if (t.type === 'checkbox' || t.type === 'radio' || t.type === 'submit' || t.type === 'button') {
                            return;
                    }

                    const select2Container =
                        t.closest && (t.closest('.select2-container') || t.closest('.select2'));
                    const insideSelect2 = !!select2Container;

                    // Skip change events from Select2 – click on option already recorded
                    if (insideSelect2) {
                        return;
                    }

                    var value = t.value !== undefined ? t.value : null;

                    rec.action = 'sendKeys';
                    rec.parsedValue = value;
                    rec.value = value;

                    rec.raw_selenium =
                        'driver.findElement(' + locator.type + '("' + locatorValue + '")).sendKeys("' + value + '");';

                    rec.raw_gherkin = 'I enter "' + value + '" into the "' + gherkinName + '"';

                    rec.options.value = value;
                    rec.options.parsedValue = value;

                    // 🔄 Debounce textboxes so we don't record partial values
                    if (role === 'textbox') {
                        const fieldKey =
                            (t.id && ('id:' + t.id)) ||
                            (t.name && ('name:' + t.name)) ||
                            ('loc:' + locator.value);

                        const existing = pendingTextChangeTimers[fieldKey];
                        if (existing && existing.timeoutId) {
                            clearTimeout(existing.timeoutId);
                        }

                        const timeoutId = setTimeout(function () {
                            persistEvent(rec);
                            delete pendingTextChangeTimers[fieldKey];
                        }, TEXT_CHANGE_DEBOUNCE_MS);

                        pendingTextChangeTimers[fieldKey] = { timeoutId, rec };
                    } else {
                        // Non-textbox (e.g. native selects) → record immediately
                        persistEvent(rec);
                    }

                    // We already persisted or scheduled persistence → don't fall through
                    return;
                }

                default:
                    return;
            }

            // Common post-processing for click (and any non-returning path):
            if (!gherkinName || gherkinName.length === 0) {
                rec.raw_gherkin = 'I interact with the unlabeled ' + gherkinRole + ' element';
            }

            persistEvent(rec);

        } catch (err) {
            console && console.log && console.log('[recorder] recordEvent error', err);
        }
    }

     // --- Oracle SSO: wrap #ssoBtn.onclick so we always record it ---
        (function installOracleSsoOnclickWrapper() {
            var attempts = 0;
            var maxAttempts = 40;   // ~20 seconds with 500ms interval
            var intervalMs = 500;

            function tryWrap() {
                attempts++;

                try {
                    var btn = document.getElementById('ssoBtn');

                    // Button not present yet → keep polling
                    if (!btn) {
                        if (attempts >= maxAttempts) {
                            window.clearInterval(poller);
                        }
                        return;
                    }

                    // Prevent double wrapping
                    if (btn.__recorderSsoWrapped) {
                        window.clearInterval(poller);
                        return;
                    }

                    btn.__recorderSsoWrapped = true;

                    var originalOnclick = btn.onclick; // may be null or a function

                    // ⬇️ THIS is the btn.onclick = function (event) { ... } block you sent
                    btn.onclick = function (event) {
                        try {
                            if (window.__recordingInstalled) {
                                var accName = getAccessibleName(btn);
                                var elementText = (btn.innerText || btn.textContent || '').trim();

                                var gherkinName =
                                    elementText ||
                                    accName ||
                                    btn.id ||
                                    'Company Single Sign-On';

                                var locator = generateSeleniumLocator(btn, accName);
                                var locatorValue = locator.value.replace(/\"/g, '\\\"');

                                var rec = {
                                    timestamp: Date.now(),
                                    type: 'click',
                                    title: document.title,
                                    action: 'click',
                                    selector: 'button',
                                    raw_gherkin: 'I click on the "' + gherkinName + '" button',
                                    raw_selenium:
                                        'driver.findElement(' +
                                        locator.type + '("' + locatorValue + '")).click();',
                                    options: {
                                        id: btn.id,
                                        name: btn.name || '',
                                        element_text: elementText,
                                        primary_name: gherkinName
                                    }
                                };

                                // Add to in-memory array
                                persistEvent(rec);

                                // 🔒 Force immediate flush for this SSO case
                                try {
                                    localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents || []));
                                } catch (e) {
                                    console && console.log &&
                                        console.log('[recorder] Oracle SSO immediate flush failed', e);
                                }

                                console && console.log &&
                                    console.log('[recorder] Oracle SSO onclick recorded');
                            }
                        } catch (err) {
                            console && console.log &&
                                console.log('[recorder] Oracle SSO onclick wrapper error', err);
                        }

                        // Always call original onclick so Oracle behavior still works
                        if (typeof originalOnclick === 'function') {
                            return originalOnclick.call(this, event);
                        }
                    };

                    console && console.log &&
                        console.log('[recorder] Wrapped #ssoBtn.onclick for Oracle SSO');

                    window.clearInterval(poller);
                } catch (err) {
                    console && console.log &&
                        console.log('[recorder] Oracle SSO onclick wrap install error', err);
                    if (attempts >= maxAttempts) {
                        window.clearInterval(poller);
                    }
                }
            }

            var poller = window.setInterval(tryWrap, intervalMs);
            // Try once immediately as well
            tryWrap();
        })();


    // Attach listeners
    ['click', 'change'].forEach(function (type) {
        window.addEventListener(type, recordEvent, true);
    });

    // ❌ No submit recording – we only care about click/change

})();