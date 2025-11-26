(function () {
    const STORAGE_KEY = '__recordedEvents';

    const TEXT_CHANGE_DEBOUNCE_MS = 700;
    const pendingTextChangeTimers = {};   // key -> { timeoutId, rec }

    // ✅ FIX 1: Deduplication State
    let lastEventSignature = '';
    let lastEventTimestamp = 0;

    let lastHoverInfo = null;

    let lsDirty = false;
    let lsFlushTimer = null;
    const LS_FLUSH_INTERVAL_MS = 1000;

    // ---- Load existing events ----
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

    // ✅ NEW: detect if we've already recorded an initial driver.get nav earlier (for this origin)
    window.__recorderHasInitialNavGet = (window.__recordedEvents || []).some(ev =>
        ev &&
        ev.type === 'navigation' &&
        typeof ev.raw_selenium === 'string' &&
        ev.raw_selenium.trim().startsWith('driver.get(')
    );

    if (window.__recordingInstalled) {
        console.log('[recorder] Already installed on this page');
        return;
    }
    window.__recordingInstalled = true;

    window.__currentUrl = window.location.href;
    console.log('[recorder] Installed on', window.location.href);

    function scheduleLocalStorageFlush() {
        if (lsFlushTimer !== null) return;

        lsFlushTimer = setTimeout(function () {
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
        // ✅ FIX 2: Strict Deduplication Logic
        const signature = `${rec.type}|${rec.action}|${rec.raw_selenium}|${rec.raw_gherkin}`;
        const now = Date.now();

        if (signature === lastEventSignature && (now - lastEventTimestamp < 150)) {
            return;
        }

        lastEventSignature = signature;
        lastEventTimestamp = now;

        (window.__recordedEvents || (window.__recordedEvents = [])).push(rec);

        lsDirty = true;
        scheduleLocalStorageFlush();
    }

    // ===================== UPDATED logNavigation =====================
    function logNavigation(url) {
        if (!window.__recordedEvents) {
            window.__recordedEvents = [];
        }

        if (!window.__recorderNavState) {
            // navCount = count of navigation events we've already logged in THIS page session
            window.__recorderNavState = { navCount: 0 };
        }

        if (!url || typeof url !== 'string' || url.length === 0) return;

        // Basic check for valid protocol
        if (!/^(http|https|file):/i.test(url)) return;

        const escapedUrl = url.replace(/\"/g, '\\\"');
        const pageTitle = document.title && document.title.trim().length > 0 ? document.title.trim() : '';

        let pageName = pageTitle || url;
        pageName = pageName.trim().replace(/\"/g, '\\"');

        const alreadyHadInitialGet = !!window.__recorderHasInitialNavGet;
        const isFirstNav = !alreadyHadInitialGet && window.__recorderNavState.navCount === 0;

        window.__recorderNavState.navCount += 1;

        let stepText;
        let rawSelenium;

        if (isFirstNav) {
            // ✅ FIRST navigation only (and only if we never wrote driver.get before on this origin):
            stepText = `I navigate to "${pageName}" page`;
            rawSelenium = 'driver.get(\"' + escapedUrl + '\");';

            // Mark that we've now produced the initial driver.get nav
            window.__recorderHasInitialNavGet = true;
        } else {
            // ✅ Later navigations: keep metadata + Gherkin, but no raw_selenium
            stepText = `I am on "${pageName}" page`;
            rawSelenium = '';
        }

        const navRec = {
            timestamp: Date.now(),
            type: 'navigation',
            action: 'navigate',
            url: url,
            title: pageTitle,
            raw_gherkin: stepText,
            raw_selenium: rawSelenium
        };

        // SSO handling logic (Original logic restored)
        let syntheticSsoClick = null;
        try {
            if (/\/oam\/server\/obrareq\.cgi/i.test(url)) {
                var now = Date.now();
                if (!window.__lastSsoSyntheticClickTs || now - window.__lastSsoSyntheticClickTs > 3000) {
                    var ssoBtn = document.getElementById('ssoBtn');
                    if (ssoBtn && window.__recordingInstalled) {
                        var accName = getAccessibleName(ssoBtn);
                        var gherkinName = (ssoBtn.innerText || ssoBtn.textContent || accName || ssoBtn.id || 'Company Single Sign-On').trim();
                        var locator = generateSeleniumLocator(ssoBtn, accName);

                        syntheticSsoClick = {
                            timestamp: now,
                            type: 'click',
                            title: document.title,
                            action: 'click',
                            selector: 'button',
                            raw_gherkin: 'I click on the "' + gherkinName + '" button',
                            raw_selenium: 'driver.findElement(' + locator.type + '("' + locator.value.replace(/\"/g, '\\\"') + '")).click();',
                            options: { id: ssoBtn.id, primary_name: gherkinName }
                        };
                        window.__lastSsoSyntheticClickTs = now;
                    }
                }
            }
        } catch (e) {}

        persistEvent(navRec);
        window.__currentUrl = url;

        if (syntheticSsoClick) {
            persistEvent(syntheticSsoClick);
            try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents || []));
            } catch (e) {
                console.log('[recorder] Failed to persist synthetic SSO click', e);
            }
        }
    }
    // ===================== END UPDATED logNavigation =====================

    // --- URL Change Detection ---
    setTimeout(() => { logNavigation(window.location.href); }, 500);

    const checkNav = () => {
        setTimeout(() => {
            if (window.location.href !== window.__currentUrl) {
                logNavigation(window.location.href);
            }
        }, 100);
    };

    window.addEventListener('popstate', checkNav);
    window.addEventListener('hashchange', checkNav);

    // ✅ FIX 3: Unload Flusher
    window.addEventListener('beforeunload', function () {
        Object.keys(pendingTextChangeTimers).forEach(key => {
            const item = pendingTextChangeTimers[key];
            if (item && item.rec) {
                (window.__recordedEvents || (window.__recordedEvents = [])).push(item.rec);
            }
        });

        try {
            if (window.__recordedEvents) {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents));
            }
        } catch (e) {
            console.log('[recorder] beforeunload flush failed', e);
        }
    });

    (function (history) {
        const pushState = history.pushState;
        history.pushState = function () {
            const result = pushState.apply(history, arguments);
            checkNav();
            return result;
        };
    })(window.history);

    // ---- Helper Functions ----

    // ================= LOCATOR ENGINE (multi-locator bundle) =================

    function le_collectAttributes(el) {
        var attrs = {};
        var list = el.attributes;
        for (var i = 0; i < list.length; i++) {
            var a = list[i];
            attrs[a.name] = a.value;
        }
        return attrs;
    }

    function le_getElementText(el) {
        var text = (el.innerText || el.textContent || '').trim();
        return text.length > 120 ? text.slice(0, 120) : text;
    }

    function le_normalizeText(text) {
        return text.replace(/\s+/g, ' ').trim();
    }

    function le_isProbablyRandomId(id) {
        if (!id) return false;

        var digits = id.match(/\d/g);
        var digitCount = digits ? digits.length : 0;
        var digitRatio = digitCount / id.length;
        if (digitRatio > 0.4 && id.length > 6) return true;

        if (/:/.test(id)) return true;                          // e.g. pt1:_UIScmil3u
        if (/^[a-f0-9\-]{8,}$/i.test(id)) return true;          // GUID-ish

        return false;
    }

    function le_addCandidate(candidates, type, value, score) {
        if (!value) return;
        candidates.push({ type: type, value: value, score: score });
    }

    function le_addDataTestCandidates(el, attrs, candidates) {
        for (var key in attrs) {
            if (!attrs.hasOwnProperty(key)) continue;
            if (/^data-(testid|test-id|qa|test|cy)$/i.test(key)) {
                var v = attrs[key];
                if (v) {
                    le_addCandidate(candidates, 'dataTest', '[' + key + '="' + v + '"]', 100);
                }
            }
        }
    }

    function le_addIdCandidates(el, attrs, candidates) {
        var id = attrs.id;
        if (!id) return;

        if (le_isProbablyRandomId(id)) {
            le_addCandidate(candidates, 'id', id, 40);
        } else {
            le_addCandidate(candidates, 'id', id, 90);
        }
    }

    function le_addNameCandidates(el, attrs, candidates) {
        var name = attrs.name;
        if (!name) return;
        var tag = el.tagName.toLowerCase();

        if (tag === 'input' || tag === 'textarea' || tag === 'select') {
            le_addCandidate(candidates, 'name', name, 80);
        } else {
            le_addCandidate(candidates, 'name', name, 60);
        }
    }

    function le_addAccessibleTextCandidates(el, attrs, candidates) {
        var ariaLabel = attrs['aria-label'];
        var titleAttr = attrs['title'];
        var altAttr = attrs['alt'];

        var texts = [];
        if (ariaLabel) texts.push(ariaLabel);
        if (titleAttr) texts.push(titleAttr);
        if (altAttr) texts.push(altAttr);

        for (var i = 0; i < texts.length; i++) {
            var norm = le_normalizeText(texts[i]);
            if (!norm) continue;

            le_addCandidate(
                candidates,
                'aria',
                '[aria-label="' + norm + '"], [title="' + norm + '"], [alt="' + norm + '"]',
                75
            );
        }
    }

    function le_addTextBasedCandidates(el, text, attrs, candidates) {
        var tag = el.tagName.toLowerCase();
        var normText = le_normalizeText(text);
        if (!normText) return;

        var typeAttr = (attrs.type || '').toLowerCase();
        var roleAttr = (attrs.role || '').toLowerCase();

        var isButtonLike =
            tag === 'button' ||
            (tag === 'input' && (typeAttr === 'button' || typeAttr === 'submit')) ||
            roleAttr === 'button';

        var isLinkLike = tag === 'a' || roleAttr === 'link';

        if (isButtonLike || isLinkLike) {
            le_addCandidate(
                candidates,
                'xpathText',
                '//' + tag + "[normalize-space(.)='" + normText + "']",
                65
            );
            le_addCandidate(
                candidates,
                'xpathText',
                "//span[normalize-space(.)='" + normText + "']/ancestor::" + tag + "[1]",
                60
            );
        }

        if (roleAttr && normText) {
            le_addCandidate(
                candidates,
                'roleText',
                "//*[@role='" + roleAttr + "'][normalize-space(.)='" + normText + "']",
                60
            );
        }
    }

    function le_addLabelTextCandidates(el, attrs, candidates) {
        var doc = el.ownerDocument;
        if (!doc) return;

        var id = attrs.id;
        if (id) {
            var label = doc.querySelector('label[for="' + id + '"]');
            if (label) {
                var lbl = le_normalizeText(label.innerText || label.textContent || '');
                if (lbl) {
                    le_addCandidate(
                        candidates,
                        'labelText',
                        "//label[normalize-space(.)='" + lbl + "']/following::" + el.tagName.toLowerCase() + "[1]",
                        60
                    );
                }
            }
        }

        var labelParent = el.closest ? el.closest('label') : null;
        if (labelParent) {
            var lbl2 = le_normalizeText(labelParent.innerText || labelParent.textContent || '');
            if (lbl2) {
                le_addCandidate(
                    candidates,
                    'labelText',
                    "//label[normalize-space(.)='" + lbl2 + "']//" + el.tagName.toLowerCase() + "[1]",
                    55
                );
            }
        }
    }

    function le_isUglyClass(c) {
        if (c.length > 25) return true;
        if (/^[a-f0-9]{8,}$/i.test(c)) return true;
        if (/^x[A-Za-z0-9]{2,}$/.test(c)) return true; // Oracle-like xmx, xo0, etc.
        return false;
    }

    function le_getElementIndexAmongSiblings(el) {
        var parent = el.parentElement;
        if (!parent) return null;

        var tag = el.tagName;
        var children = parent.children;
        var sameTag = [];
        for (var i = 0; i < children.length; i++) {
            if (children[i].tagName === tag) sameTag.push(children[i]);
        }
        for (var j = 0; j < sameTag.length; j++) {
            if (sameTag[j] === el) return j;
        }
        return null;
    }

    function le_addCssFallbackCandidates(el, attrs, candidates) {
        var tag = el.tagName.toLowerCase();
        var classAttr = attrs['class'] || '';
        var classes = classAttr.split(/\s+/).filter(function (c) { return !!c; });

        if (classes.length === 1 && !le_isUglyClass(classes[0])) {
            le_addCandidate(candidates, 'css', tag + '.' + classes[0], 50);
        }

        if (classes.length > 1) {
            var good = [];
            for (var i = 0; i < classes.length; i++) {
                if (!le_isUglyClass(classes[i])) good.push(classes[i]);
            }
            if (good.length > 0) {
                var css = tag;
                for (var j = 0; j < good.length; j++) {
                    css += '.' + good[j];
                }
                le_addCandidate(candidates, 'css', css, 45);
            }
        }

        var idx = le_getElementIndexAmongSiblings(el);
        if (idx !== null && idx < 10) {
            le_addCandidate(
                candidates,
                'css',
                tag + ':nth-of-type(' + (idx + 1) + ')',
                20
            );
        }
    }

    function le_dedupeAndSort(candidates) {
        var map = {};
        for (var i = 0; i < candidates.length; i++) {
            var c = candidates[i];
            var key = c.type + '|' + c.value;
            if (!map[key] || c.score > map[key].score) {
                map[key] = c;
            }
        }
        var out = [];
        for (var k in map) {
            if (map.hasOwnProperty(k)) out.push(map[k]);
        }
        out.sort(function (a, b) { return b.score - a.score; });
        return out;
    }

    // 🔹 PUBLIC: buildRecordedTarget(el)
    function buildRecordedTarget(el) {
        if (!el || el.nodeType !== 1) return null;
        var element = el;
        var attrs = le_collectAttributes(element);
        var text = le_getElementText(element);
        var candidates = [];

        le_addDataTestCandidates(element, attrs, candidates);
        le_addIdCandidates(element, attrs, candidates);
        le_addNameCandidates(element, attrs, candidates);
        le_addAccessibleTextCandidates(element, attrs, candidates);
        le_addTextBasedCandidates(element, text, attrs, candidates);
        le_addLabelTextCandidates(element, attrs, candidates);
        le_addCssFallbackCandidates(element, attrs, candidates);

        var locators = le_dedupeAndSort(candidates);

        return {
            tagName: element.tagName.toLowerCase(),
            text: text,
            attributes: attrs,
            locators: locators
        };
    }

    // ---- Existing helpers: accessible name + basic locator for raw_selenium ----

    function getAccessibleName(t) {
        if (!t) return '';
        var original = t;
        var el = t;

        // 1. Ancestor check
        while (el) {
            if (el.getAttribute) {
                var aria = el.getAttribute('aria-label');
                if (aria && aria.trim().length > 0) return aria.trim();

                var titleAttr = el.getAttribute('title');
                if (titleAttr && titleAttr.trim().length > 0) return titleAttr.trim();

                if (el.tagName && el.tagName.toLowerCase() === 'img') {
                    var alt = el.getAttribute('alt');
                    if (alt && alt.trim().length > 0) return alt.trim();
                }
            }
            el = el.parentElement;
        }

        // 2. Label for
        var id = original.id;
        if (id) {
            var labelEl = document.querySelector('label[for="' + id + '"]');
            if (labelEl) return (labelEl.innerText || labelEl.textContent || '').trim();
        }

        // 3. Name
        if (original.name) return original.name.trim();

        // 4. Inner Text fallback
        el = original;
        while (el) {
            var txt = (el.innerText || el.textContent || '').trim();
            if (txt.length > 0) return txt;
            el = el.parentElement;
        }
        return '';
    }

    function generateSeleniumLocator(t, accName) {
        if (!t) return { type: 'By.cssSelector', value: '*' };

        var id = t.id || '';
        var name = t.name || '';
        var tag = (t.tagName || '').toLowerCase();
        var elementText = (t.innerText || t.textContent || '').trim();
        var normalizedText = elementText.replace(/'/g, "\\'");
        var normalizedAccName = (accName || '').replace(/'/g, "\\'");

        if (tag === 'a' && elementText.length > 0) {
            return { type: 'By.linkText', value: elementText };
        }

        if (elementText.length > 0 && tag !== 'input' && tag !== 'textarea') {
            var xpath = "//" + tag + "[normalize-space(.)='" + normalizedText + "']";
            return { type: 'By.xpath', value: xpath };
        }
        if (name && name.length > 0) return { type: 'By.name', value: name };
        if (id && id.length > 0) return { type: 'By.id', value: id };

        if (normalizedAccName.length > 0) {
            var xpath2 = "//label[normalize-space(.)='" + normalizedAccName + "']/following-sibling::" + tag +
                " | //" + tag + "[@aria-label='" + normalizedAccName + "']" +
                " | //" + tag + "[@placeholder='" + normalizedAccName + "']";
            return { type: 'By.xpath', value: xpath2 };
        }

        return { type: 'By.cssSelector', value: tag || '*' };
    }

    // --- Specific Dropdown Option Handler ---
    function handleDropdownOptionEvent(e) {
        try {
            if (!e || !e.target || !e.target.closest) return;

            const optionEl = e.target.closest(
                '.select2-results__option, li[role="option"], [role="option"], li[aria-selected]'
            );

            if (!optionEl) return;

            const optionText = (optionEl.innerText || optionEl.textContent || '').trim();
            if (!optionText) return;

            const rec = {
                timestamp: Date.now(),
                type: 'click',
                title: document.title,
                action: 'click',
                selector: 'option',
                options: { element_text: optionText, primary_name: optionText }
            };

            const escapedText = optionText.replace(/'/g, "\\'");
            let locatorExpr;

            if (optionEl.classList.contains('select2-results__option')) {
                locatorExpr = 'By.xpath("//li[contains(@class,\'select2-results__option\') and normalize-space()=\'' + escapedText + '\']")';
            } else if (optionEl.getAttribute('role') === 'option') {
                locatorExpr = 'By.xpath("(//*[@role=\'option\' and normalize-space()=\'' + escapedText + '\'])[1]")';
            } else {
                locatorExpr = 'By.xpath("(//*[normalize-space()=\'' + escapedText + '\'])[1]")';
            }

            rec.raw_selenium = 'driver.findElement(' + locatorExpr + ').click();';
            rec.raw_gherkin = 'I select "' + optionText + '" from the dropdown';

            // optional: we could attach a target here too using buildRecordedTarget(optionEl)
            persistEvent(rec);
        } catch (err) {}
    }

    document.addEventListener('mouseup', handleDropdownOptionEvent, true);
    document.addEventListener('click', handleDropdownOptionEvent, true);

    // ---- Main Event Recorder (patched with normalizeClickableTarget + target bundle) ----
    function recordEvent(e) {
        try {
            if (!e || !e.isTrusted) return;

            var t = e.target;
            if (!t || t.nodeType !== 1) return;

            // ✅ Normalize custom radios/checkboxes etc.
            if (e.type === 'click') {
                t = normalizeClickableTarget(t);
                if (!t || t.nodeType !== 1) return;
            }

            // ✅ Bubble up to semantic clickable, but don't override real radio/checkbox input
            if (e.type === 'click' && t.closest) {
                const isCheckLike =
                    t.tagName &&
                    t.tagName.toLowerCase() === 'input' &&
                    (t.type === 'checkbox' || t.type === 'radio');

                if (!isCheckLike) {
                    let clickable = t.closest('a[href], button, [role="button"], [role="link"], [onclick]');
                    if (clickable) t = clickable;
                }
            }

            const tag = t.tagName.toLowerCase();

            // Filter out non-interactive clicks
            const isInteractive =
                ['a', 'button', 'input', 'select', 'textarea'].includes(tag) ||
                t.getAttribute('role') ||
                t.onclick ||
                (t.closest && t.closest('[onclick]'));

            if (e.type === 'click' && !isInteractive) {
                const txt = (t.innerText || t.textContent || '').trim();
                if (!t.id && (!txt || txt.length > 20)) {
                    return;
                }
            }

            var rec = {};
            rec.timestamp = Date.now();
            rec.type = e.type;
            rec.title = document.title;

            var accName = getAccessibleName(t);
            var elementText = (t.innerText || t.textContent || '').trim();

            // 🔹 NEW: attach full locator bundle for this target
            var targetInfo = buildRecordedTarget(t);
            if (targetInfo) {
                rec.target = targetInfo;
            }

            // 🔸 SPECIAL CASE 1: icon-only hamburger menu link
            if (
                e.type === 'click' &&
                tag === 'a' &&
                (!elementText || elementText.length === 0) &&
                t.classList &&
                t.classList.contains('sidenav_hamburger-icon')
            ) {
                var gherkinNameHamburger = 'navigation menu';
                var cssHamburger = 'a.sidenav_hamburger-icon';

                rec.action = 'click';
                rec.selector = 'a';
                rec.options = {
                    element_text: '',
                    primary_name: gherkinNameHamburger
                };

                rec.raw_gherkin = 'I click on the "' + gherkinNameHamburger + '" link';
                rec.raw_selenium =
                    'driver.findElement(By.cssSelector("' + cssHamburger + '")).click();';

                persistEvent(rec);
                return;
            }

            // 🔸 SPECIAL CASE 2: Select2 "open dropdown" click
            if (e.type === 'click' && t.closest) {
                const select2Container =
                    t.closest('.select2-container') || t.closest('.select2');

                const isOption =
                    t.closest('.select2-results__option') ||
                    t.closest('[role="option"]') ||
                    t.closest('li[aria-selected]');

                if (select2Container && !isOption) {
                    let selectionEl =
                        select2Container.querySelector('.select2-selection[id], .select2-selection__rendered[id]') ||
                        select2Container.querySelector('[id^="select2-"][id$="-container"]');

                    if (!selectionEl) {
                        selectionEl = select2Container;
                    }

                    const idToUse = (selectionEl && selectionEl.id) ? selectionEl.id : '';

                    const currentLabel = elementText || accName || idToUse || 'dropdown';

                    rec.action = 'click';
                    rec.selector = 'dropdown';
                    rec.options = {
                        element_text: elementText,
                        primary_name: currentLabel
                    };

                    rec.raw_gherkin = 'I open the "' + currentLabel + '" dropdown';

                    if (idToUse) {
                        rec.raw_selenium =
                            'driver.findElement(By.id("' + idToUse + '")).click();';
                    } else {
                        rec.raw_selenium =
                            'driver.findElement(By.cssSelector(".select2-container .select2-selection")).click();';
                    }

                    persistEvent(rec);
                    return;
                }
            }

            // --- Normal path for everything else ---
            var locator = generateSeleniumLocator(t, accName);
            var locatorValue = locator.value.replace(/\"/g, '\\\"');

            var gherkinName = elementText || accName || t.name || t.id || 'element';

            rec.options = {
                element_text: elementText,
                primary_name: gherkinName
            };

            // 🔹 Synthetic HOVER step before click, if needed
            if (e.type === 'click' && lastHoverInfo && lastHoverInfo.element && lastHoverInfo.locator) {
                try {
                    const hoverAge = Date.now() - lastHoverInfo.timestamp;
                    const HOVER_MAX_AGE_MS = 800;

                    // Only if recent and the click is inside the hover root
                    if (hoverAge <= HOVER_MAX_AGE_MS &&
                        lastHoverInfo.element.contains(t)) {

                        const hoverLoc = lastHoverInfo.locator;
                        const hoverLocValue = hoverLoc.value.replace(/\"/g, '\\\"');
                        const hoverName = lastHoverInfo.gherkinName || 'element';

                        const hoverRec = {
                            timestamp: Date.now(),
                            type: 'hover',
                            action: 'hover',
                            title: document.title,
                            selector: (lastHoverInfo.element.tagName || '').toLowerCase(),
                            options: {
                                element_text: lastHoverInfo.element.innerText || lastHoverInfo.element.textContent || '',
                                primary_name: hoverName
                            },
                            raw_gherkin: 'I hover over "' + hoverName + '"',
                            raw_selenium:
                                'new org.openqa.selenium.interactions.Actions(driver)' +
                                '.moveToElement(driver.findElement(' + hoverLoc.type + '("' + hoverLocValue + '")))' +
                                '.pause(java.time.Duration.ofMillis(300)).perform();'
                        };

                        if (lastHoverInfo.targetInfo) {
                            hoverRec.target = lastHoverInfo.targetInfo;
                        }

                        // Persist hover BEFORE the actual click step
                        persistEvent(hoverRec);

                        // We only want one hover per click sequence
                        lastHoverInfo = null;
                    }
                } catch (hoverErr) {
                    console.log('[recorder] synthetic hover generation error', hoverErr);
                }
            }

            // === Click Handling ===
            if (e.type === 'click') {
                rec.action = 'click';
                rec.selector = tag;

                // Don't record clicks on text inputs (we only care about what is typed)
                if (tag === 'input' && ['text', 'password', 'email', 'search'].includes(t.type)) {
                    return;
                }

                if (tag === 'a') {
                    rec.raw_gherkin = 'I click on the "' + gherkinName + '" link';
                } else if (tag === 'button' || t.getAttribute('role') === 'button') {
                    rec.raw_gherkin = 'I click on the "' + gherkinName + '" button';
                } else {
                    rec.raw_gherkin = 'I click on "' + gherkinName + '"';
                }

                rec.raw_selenium =
                    'driver.findElement(' + locator.type + '("' + locatorValue + '")).click();';

                persistEvent(rec);
            }

            // === Change Handling ===
            if (e.type === 'change') {
                // Skip checkbox / radio – usually handled by click
                if (t.type === 'checkbox' || t.type === 'radio') return;

                var value = t.value;
                rec.action = 'sendKeys';
                rec.selector = tag;
                rec.value = value;

                rec.raw_gherkin = 'I enter "' + value + '" into "' + gherkinName + '"';
                rec.raw_selenium =
                    'driver.findElement(' + locator.type + '("' + locatorValue + '")).sendKeys("' + value + '");';

                const fieldKey = (t.id ? 'id:' + t.id : 'loc:' + locator.value);

                if (pendingTextChangeTimers[fieldKey]) {
                    clearTimeout(pendingTextChangeTimers[fieldKey].timeoutId);
                }

                pendingTextChangeTimers[fieldKey] = {
                    timeoutId: setTimeout(function () {
                        persistEvent(rec);
                        delete pendingTextChangeTimers[fieldKey];
                    }, TEXT_CHANGE_DEBOUNCE_MS),
                    rec: rec
                };
            }

        } catch (err) {
            console.log('[recorder] recordEvent error', err);
        }
    }

    // ✅ Target normalizer (custom radios/checkboxes)
    function normalizeClickableTarget(target) {
        if (!target || !target.closest) return target;

        let t = target;

        // Special case: custom radio/checkbox like <span class="fds_radio__custom-radio">
        if (t.matches && t.matches('.fds_radio__custom-radio')) {
            const wrapper = t.closest('label, .fds_radio, .fds_radio__wrapper, .fds_radio__container');
            if (wrapper) {
                const realInput = wrapper.querySelector('input[type="radio"], input[type="checkbox"]');
                if (realInput) {
                    return realInput;
                }
            }
        }

        // Generic case: anything inside a label that owns an input
        const label = t.closest('label');
        if (label) {
            const input = label.querySelector('input[type="radio"], input[type="checkbox"], input[type="checkbox"]');
            if (input) {
                return input;
            }
        }

        return t;
    }

    function xpathLiteral(text) {
        if (!text.includes("'")) {
            return `'${text}'`;
        }
        const parts = text.split("'");
        return "concat(" + parts.map((p, i) =>
            (i > 0 ? "\"'\", " : "") + `'${p}'`
        ).join("") + ")";
    }

    function isHoverRoot(el) {
        if (!el || !el.tagName) return false;
        const tag = el.tagName.toLowerCase();
        const cls = el.className || '';
        const role = (el.getAttribute && el.getAttribute('role')) || '';

        // You can tune this list based on your app
        if (tag === 'a' || tag === 'button') return true;
        if (role === 'button' || role === 'menuitem') return true;

        // Typical "user menu" / navbar icons etc.
        if (cls.indexOf('sidenav_option-icon') !== -1) return true;
        if (cls.indexOf('menu') !== -1 || cls.indexOf('dropdown') !== -1) return true;

        return false;
    }

    function handleMouseOver(e) {
        try {
            if (!e || !e.target || !e.isTrusted) return;
            let el = e.target;
            if (!el.closest) return;

            // Walk up to a reasonable "hover root"
            let root = el.closest('a, button, [role="button"], [role="menuitem"], .sidenav_option-icon, .menu, .dropdown-toggle, .oj-menu');
            if (!root || !isHoverRoot(root)) return;

            const accName = getAccessibleName(root);
            const elementText = (root.innerText || root.textContent || '').trim();
            const gherkinName = elementText || accName || root.id || root.name || 'element';

            const locator = generateSeleniumLocator(root, accName);
            const targetInfo = buildRecordedTarget(root);

            lastHoverInfo = {
                element: root,
                timestamp: Date.now(),
                locator: locator,
                targetInfo: targetInfo,
                gherkinName: gherkinName
            };
        } catch (err) {
            console.log('[recorder] handleMouseOver error', err);
        }
    }

    // Register the hover listener (capture = true to see it early)
    window.addEventListener('mouseover', handleMouseOver, true);


    // --- Oracle SSO Wrapper (Restored) ---
    (function installOracleSsoOnclickWrapper() {
        var attempts = 0;
        var maxAttempts = 40;
        function tryWrap() {
            attempts++;
            try {
                var btn = document.getElementById('ssoBtn');
                if (!btn) {
                    if (attempts < maxAttempts) return;
                    else return clearInterval(poller);
                }
                if (btn.__recorderSsoWrapped) return clearInterval(poller);

                btn.__recorderSsoWrapped = true;
                var originalOnclick = btn.onclick;

                btn.onclick = function (event) {
                    if (window.__recordingInstalled) {
                        var accName = getAccessibleName(btn);
                        var gherkinName = (btn.innerText || accName || 'SSO Button').trim();
                        var rec = {
                            timestamp: Date.now(),
                            type: 'click',
                            action: 'click',
                            raw_gherkin: 'I click on "' + gherkinName + '"',
                            raw_selenium: 'driver.findElement(By.id("ssoBtn")).click();'
                        };
                        // attach target bundle for SSO also
                        var targetInfo = buildRecordedTarget(btn);
                        if (targetInfo) {
                            rec.target = targetInfo;
                        }
                        persistEvent(rec);
                        try {
                            localStorage.setItem(STORAGE_KEY, JSON.stringify(window.__recordedEvents));
                        } catch (e) {
                            console.log('[recorder] Failed to persist SSO wrapper click', e);
                        }
                    }
                    if (typeof originalOnclick === 'function') return originalOnclick.call(this, event);
                };
                clearInterval(poller);
            } catch (err) { if (attempts >= maxAttempts) clearInterval(poller); }
        }
        var poller = window.setInterval(tryWrap, 500);
    })();

    ['click', 'change'].forEach(type => window.addEventListener(type, recordEvent, true));

    // ============================================================
    //  Recording Save UI: custom name, timestamp, recent names, preview
    // ============================================================

    const LS_KEY_LAST_NAME = '__recorder_lastRecordingName';
    const LS_KEY_RECENT_NAMES = '__recorder_recentRecordingNames';
    const MAX_RECENT_NAMES = 10;

    function sanitizeName(raw) {
        if (!raw) return '';
        // Only allow letters, numbers, underscore, hyphen; others → "_"
        return raw.replace(/[^a-zA-Z0-9_\-]/g, '_');
    }

    function makeTimestampForFile() {
        const now = new Date();
        return (
            now.getFullYear().toString() +
            String(now.getMonth() + 1).padStart(2, '0') +
            String(now.getDate()).padStart(2, '0') + '_' +
            String(now.getHours()).toString().padStart(2, '0') +
            String(now.getMinutes()).toString().padStart(2, '0') +
            String(now.getSeconds()).toString().padStart(2, '0')
        );
    }

    function buildFilename(baseName, withTimestamp) {
        const clean = sanitizeName(baseName || 'recording');
        if (withTimestamp) {
            return `${clean}_${makeTimestampForFile()}.json`;
        }
        return `${clean}.json`;
    }

    function loadRecentNames() {
        try {
            const raw = localStorage.getItem(LS_KEY_RECENT_NAMES);
            if (!raw) return [];
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            console.warn('[recorder] Failed to load recent names', e);
            return [];
        }
    }

    function saveRecentName(name) {
        const clean = sanitizeName(name);
        if (!clean) return;

        let arr = loadRecentNames();
        // Remove if already present, then unshift to front
        arr = arr.filter(n => n !== clean);
        arr.unshift(clean);
        if (arr.length > MAX_RECENT_NAMES) {
            arr = arr.slice(0, MAX_RECENT_NAMES);
        }

        try {
            localStorage.setItem(LS_KEY_RECENT_NAMES, JSON.stringify(arr));
            localStorage.setItem(LS_KEY_LAST_NAME, clean);
        } catch (e) {
            console.warn('[recorder] Failed to save recent name', e);
        }
    }

    function initRecordingNameUi() {
        if (window.__recorderNameUiInitialized) return;
        window.__recorderNameUiInitialized = true;

        const nameInput = document.getElementById('recordingName');
        const tsCheckbox = document.getElementById('addTimestamp');
        const preview = document.getElementById('recordingFilenamePreview');
        const recentSelect = document.getElementById('recordingRecentNames');
        const saveBtn = document.getElementById('saveQuitBtn');
        const statusBox = document.getElementById('status-message');

        // If none of these exist on this page, do nothing.
        if (!nameInput && !saveBtn) {
            return;
        }

        // Load last used name
        if (nameInput) {
            try {
                const last = localStorage.getItem(LS_KEY_LAST_NAME);
                if (last) {
                    nameInput.value = last;
                }
            } catch (e) {
                console.warn('[recorder] Failed to load last recording name', e);
            }
        }

        // Populate recent names dropdown
        if (recentSelect) {
            const recent = loadRecentNames();
            recent.forEach(name => {
                const opt = document.createElement('option');
                opt.value = name;
                opt.textContent = name;
                recentSelect.appendChild(opt);
            });

            recentSelect.addEventListener('change', () => {
                if (recentSelect.value && nameInput) {
                    nameInput.value = recentSelect.value;
                    updatePreview();
                }
            });
        }

        function updatePreview() {
            if (!preview || !nameInput) return;
            const withTs = tsCheckbox ? tsCheckbox.checked : true;
            const fn = buildFilename(nameInput.value.trim(), withTs);
            preview.textContent = fn;
        }

        if (nameInput) {
            nameInput.addEventListener('input', updatePreview);

            // Enter key = Save
            nameInput.addEventListener('keydown', (ev) => {
                if (ev.key === 'Enter') {
                    ev.preventDefault();
                    if (saveBtn) {
                        saveBtn.click();
                    }
                }
            });
        }

        if (tsCheckbox) {
            tsCheckbox.addEventListener('change', updatePreview);
        }

        // Initial preview
        updatePreview();

        if (saveBtn) {
            saveBtn.addEventListener('click', function () {
                try {
                    const events = window.__recordedEvents || [];
                    if (!Array.isArray(events) || events.length === 0) {
                        alert('No recorded events to save.');
                        return;
                    }

                    const nameEl = nameInput;
                    const tsEl = tsCheckbox;

                    let customName = nameEl ? nameEl.value.trim() : '';
                    if (!customName) {
                        alert('Please enter a name for this recording.');
                        if (nameEl) nameEl.focus();
                        return;
                    }

                    const withTs = tsEl ? tsEl.checked : true;
                    const fileName = buildFilename(customName, withTs);

                    // Remember name for next time
                    saveRecentName(customName);

                    const blob = new Blob([JSON.stringify(events, null, 2)], {
                        type: 'application/json'
                    });

                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = fileName;
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    URL.revokeObjectURL(url);

                    console.log('[recorder] Saved recording as', fileName);
                    if (statusBox) {
                        statusBox.textContent = `Saved recording as ${fileName}`;
                    } else {
                        alert(`Saved recording as ${fileName}`);
                    }
                } catch (e) {
                    console.error('[recorder] Failed to save recording', e);
                    alert('Failed to save recording: ' + e.message);
                }
            });
        }
    }

    // Initialize UI once DOM is ready (if this page actually has those elements)
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(initRecordingNameUi, 0);
    } else {
        document.addEventListener('DOMContentLoaded', initRecordingNameUi);
    }

})();
