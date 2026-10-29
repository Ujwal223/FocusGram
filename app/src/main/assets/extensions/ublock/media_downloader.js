/**
 * In-page media downloader injected into web contexts.
 * Attaches download buttons to feed posts, reels, and profiles, resolving media URLs
 * via GraphQL, React Fiber, DOM inspection, and performance resource timings before
 * delegating to the native host over custom URI schemes or extension messaging.
 */

'use strict';

(function () {
    function isAuthPage() {
        try {
            var path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) return false;
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') || path.startsWith('/two_factor') || path.startsWith('/challenge')) return true;
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], form input[type="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return;

    if (window.__fgMediaDownloaderInstalled) return;
    window.__fgMediaDownloaderInstalled = true;

    const FG_DL_ATTR = 'data-fg-download-btn';
    const IG_POST_ROOT_QUERY_DOC_ID = '27852811784380813';

    let downloadEnabled = false;

    function isDownloadEnabled() {
        if (typeof downloadEnabled === 'boolean' && downloadEnabled) return true;
        if (window.__FOCUSGRAM_CONFIG__ && window.__FOCUSGRAM_CONFIG__.videoDownloadEnabled !== undefined) {
            return !!window.__FOCUSGRAM_CONFIG__.videoDownloadEnabled;
        }
        return false;
    }

    if (typeof browser !== 'undefined' && browser.runtime) {
        if (browser.runtime.onMessage) {
            browser.runtime.onMessage.addListener((msg) => {
                if (msg && msg.action === 'updateSettings' && msg.settings) {
                    if (msg.settings.videoDownloadEnabled !== undefined) {
                        downloadEnabled = !!msg.settings.videoDownloadEnabled;
                        syncDownloadButtons();
                        syncProfileDownloadButton();
                        syncReelsDownloadButton();
                    }
                }
            });
        }
        if (browser.runtime.sendMessage) {
            try {
                const res = browser.runtime.sendMessage({ action: 'getSettings' });
                if (res && typeof res.then === 'function') {
                    res.then((r) => {
                        if (r && r.settings && r.settings.videoDownloadEnabled !== undefined) {
                            downloadEnabled = !!r.settings.videoDownloadEnabled;
                            syncDownloadButtons();
                            syncProfileDownloadButton();
                            syncReelsDownloadButton();
                        }
                    }).catch(() => {});
                }
            } catch (_) {}
        }
    }

    let isGlobalScrolling = false;
    let globalScrollTimeout = null;
    let lastScrollTime = 0;
    window.addEventListener('scroll', () => {
        isGlobalScrolling = true;
        lastScrollTime = Date.now();
        clearTimeout(globalScrollTimeout);
        globalScrollTimeout = setTimeout(() => {
            isGlobalScrolling = false;
        }, 250);
    }, { passive: true, capture: true });

    function triggerNativeDownload(url, filename, error) {
        if (url) {
            try {
                window.location.href = 'focusgram-download://download?url=' + encodeURIComponent(url) + '&filename=' + encodeURIComponent(filename || '');
            } catch (_) {}
        }

        try {
            if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({
                    action: 'downloadMedia',
                    url: url || '',
                    filename: filename || '',
                    error: error || ''
                });
                return;
            }
        } catch (_) {}

        try {
            window.dispatchEvent(new CustomEvent('focusgram:download-request', {
                detail: { url: url || '', filename: filename || '', error: error || '' }
            }));
            const targetOrigin = (window.location.origin && window.location.origin !== 'null') ? window.location.origin : window.location.href;
            window.postMessage({
                type: 'FOCUSGRAM_DOWNLOAD_REQUEST',
                url: url || '',
                filename: filename || '',
                error: error || ''
            }, targetOrigin);
        } catch (e) {
            console.error('[FocusGram Downloader] triggerNativeDownload error:', e);
        }
    }

    function getCookie(name) {
        try {
            const match = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()\[\]\\\/+^])/g, '\\$1') + '=([^;]*)'));
            return match ? decodeURIComponent(match[1]) : '';
        } catch (_) {}
        return '';
    }

    function getFbDtsg() {
        try {
            for (const script of document.scripts) {
                const text = script.textContent || '';
                const match = text.match(/"DTSGInitialData",\[\],\{"token":"([^"]+)"/) || text.match(/"dtsg":\s*\{\s*"token":"([^"]+)"/);
                if (match) return match[1];
            }
        } catch (_) {}
        return '';
    }

    function parseMediaItem(item, slideIndex = 0) {
        if (!item) return null;

        if (item.carousel_media && Array.isArray(item.carousel_media) && item.carousel_media.length > 0) {
            const target = item.carousel_media[slideIndex] || item.carousel_media[0];
            const parsed = parseMediaItem(target, 0);
            if (parsed) {
                parsed.id = item.pk || item.id || parsed.id;
                parsed.code = item.code || parsed.code;
                parsed.author = item.user?.username || parsed.author;
                return parsed;
            }
        }
        if (item.edge_sidecar_to_children?.edges && Array.isArray(item.edge_sidecar_to_children.edges) && item.edge_sidecar_to_children.edges.length > 0) {
            const edge = item.edge_sidecar_to_children.edges[slideIndex] || item.edge_sidecar_to_children.edges[0];
            const target = edge?.node;
            if (target) {
                const parsed = parseMediaItem(target, 0);
                if (parsed) {
                    parsed.id = item.id || parsed.id;
                    parsed.code = item.shortcode || parsed.code;
                    parsed.author = item.owner?.username || parsed.author;
                    return parsed;
                }
            }
        }

        const isVideo = !!(item.is_video || item.media_type === 2 || (item.video_versions && item.video_versions.length > 0) || item.video_url);
        let videoUrl = item.video_url || '';
        let imageUrl = item.display_url || '';

        if (item.video_versions && Array.isArray(item.video_versions) && item.video_versions.length > 0) {
            videoUrl = item.video_versions[0]?.url || videoUrl;
        }
        if (item.image_versions2?.candidates && Array.isArray(item.image_versions2.candidates) && item.image_versions2.candidates.length > 0) {
            imageUrl = item.image_versions2.candidates[0]?.url || imageUrl;
        } else if (item.display_resources && Array.isArray(item.display_resources) && item.display_resources.length > 0) {
            imageUrl = item.display_resources[item.display_resources.length - 1]?.src || imageUrl;
        }

        return {
            isVideo: isVideo && !!videoUrl,
            url: (isVideo && videoUrl) ? videoUrl : (imageUrl || videoUrl),
            videoUrl: videoUrl,
            imageUrl: imageUrl,
            code: item.code || item.shortcode || '',
            id: item.pk || item.id || '',
            author: item.user?.username || item.owner?.username || '',
            caption: item.caption?.text || item.edge_media_to_caption?.edges?.[0]?.node?.text || ''
        };
    }

    async function fetchMediaByShortcode(shortcode, slideIndex = 0) {
        if (!shortcode) return null;

        try {
            const apiURL = new URL('/graphql/query', window.location.origin);
            const headers = {
                'content-type': 'application/x-www-form-urlencoded',
                'x-fb-friendly-name': 'PolarisPostRootQuery',
                'x-ig-app-id': '936619743392459',
                'x-requested-with': 'XMLHttpRequest',
            };
            const csrfToken = getCookie('csrftoken');
            if (csrfToken) headers['x-csrftoken'] = csrfToken;

            const fbDtsg = getFbDtsg();
            const bodyParams = {
                fb_api_caller_class: 'RelayModern',
                fb_api_req_friendly_name: 'PolarisPostRootQuery',
                doc_id: IG_POST_ROOT_QUERY_DOC_ID,
                variables: JSON.stringify({
                    shortcode: shortcode,
                    __relay_internal__pv__PolarisShortDramaEnabledrelayprovider: false,
                    __relay_internal__pv__PolarisMultiCaptionCarouselEnabledrelayprovider: false,
                }),
                server_timestamps: 'true',
            };
            if (fbDtsg) bodyParams.fb_dtsg = fbDtsg;

            const res = await fetch(apiURL.href, {
                method: 'POST',
                headers: headers,
                body: new URLSearchParams(bodyParams).toString(),
                credentials: 'include'
            });

            if (res.ok) {
                const json = await res.json();
                const item = json?.data?.['xdt_api__v1__media__shortcode__web_info']?.items?.[0];
                if (item) {
                    return parseMediaItem(item, slideIndex);
                }
            }
        } catch (e) {
            console.warn('[FocusGram Downloader] GraphQL shortcode query error:', e);
        }

        try {
            const res = await fetch(`/p/${shortcode}/?__a=1&__d=dis`, {
                headers: {
                    'x-ig-app-id': '936619743392459',
                    'x-requested-with': 'XMLHttpRequest'
                },
                credentials: 'include'
            });
            if (res.ok) {
                const json = await res.json();
                const item = json?.items?.[0] || json?.graphql?.shortcode_media;
                if (item) return parseMediaItem(item, slideIndex);
            }
        } catch (_) {}

        return null;
    }

    function isLikelyImageUrl(url) {
        if (!url || typeof url !== 'string') return false;
        const lower = url.toLowerCase();
        return lower.includes('.jpg') || lower.includes('.jpeg') || lower.includes('.png') ||
               lower.includes('.webp') || lower.includes('dst-jpg') || lower.includes('dst-png') ||
               lower.includes('dst-webp') || lower.startsWith('data:image');
    }

    function isLikelyVideoUrl(url) {
        if (!url || typeof url !== 'string') return false;
        if (isLikelyImageUrl(url)) return false;
        const lower = url.toLowerCase();
        return lower.includes('.mp4') || lower.includes('.webm') || lower.includes('/v/t50.') ||
               lower.includes('/v/t16.') || lower.includes('video_dashinit') || lower.startsWith('data:video');
    }

    function getActiveStreamFromPerformance() {
        try {
            const perf = (window.wrappedJSObject && window.wrappedJSObject.performance) || window.performance;
            if (!perf || typeof perf.getEntriesByType !== 'function') return null;
            const entries = perf.getEntriesByType('resource');
            for (let i = entries.length - 1; i >= 0 && i >= entries.length - 100; i--) {
                const entry = entries[i];
                const name = entry.name || '';
                if (!name.startsWith('http://') && !name.startsWith('https://')) continue;
                if (isLikelyImageUrl(name)) continue;
                if (!name.includes('cdninstagram.com') && !name.includes('fbcdn.net')) continue;

                if (name.includes('.mp4') || name.includes('/v/t50.') || name.includes('/v/t16.') ||
                    name.includes('video_dashinit') || (name.includes('bytestart') && !isLikelyImageUrl(name))) {
                    try {
                        const u = new URL(name);
                        u.searchParams.delete('bytestart');
                        u.searchParams.delete('byteend');
                        return { url: u.toString(), isVideo: true };
                    } catch (_) {
                        const cleanUrl = name.replace(/[?&]bytestart=\d+/, '').replace(/[?&]byteend=\d+/, '');
                        return { url: cleanUrl, isVideo: true };
                    }
                }
            }
        } catch (_) {}
        return null;
    }

    function extractFromFiber(node) {
        if (!node) return null;
        try {
            const key = Object.keys(node).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));
            if (!key) return null;
            let fiber = node[key];
            for (let i = 0; i < 40 && fiber; i++) {
                const props = fiber.memoizedProps || fiber.pendingProps;
                if (props) {
                    const author = props.user?.username || props.owner?.username || props.media?.user?.username || props.media?.owner?.username || props.item?.user?.username || props.clip?.user?.username || '';
                    const code = props.code || props.shortcode || props.media?.code || props.media?.shortcode || props.item?.code || props.clip?.code || props.post?.code || '';

                    if (props.video_versions && Array.isArray(props.video_versions) && props.video_versions[0]?.url) {
                        return { url: props.video_versions[0].url, isVideo: true, code: code, author: author };
                    }
                    if (props.media?.video_versions?.[0]?.url) {
                        return { url: props.media.video_versions[0].url, isVideo: true, code: code, author: author };
                    }
                    if (props.item?.video_versions?.[0]?.url) {
                        return { url: props.item.video_versions[0].url, isVideo: true, code: code, author: author };
                    }
                    if (props.clip?.video_versions?.[0]?.url) {
                        return { url: props.clip.video_versions[0].url, isVideo: true, code: code, author: author };
                    }
                    if (props.post?.video_versions?.[0]?.url) {
                        return { url: props.post.video_versions[0].url, isVideo: true, code: code, author: author };
                    }
                    if (props.videoUrl && typeof props.videoUrl === 'string' && props.videoUrl.startsWith('http') && isLikelyVideoUrl(props.videoUrl)) {
                        return { url: props.videoUrl, isVideo: true, code: code, author: author };
                    }
                    if (props.display_url && typeof props.display_url === 'string' && props.display_url.startsWith('http')) {
                        return { url: props.display_url, isVideo: false, code: code, author: author };
                    }
                    if (code || author) {
                        return { code: code, author: author };
                    }
                }
                fiber = fiber.return;
            }
        } catch (_) {}
        return null;
    }

    function extractBestDomUrl(container, isVideoExpected = false) {
        if (!container) return null;

        const video = container.querySelector('video') || (container.tagName === 'VIDEO' ? container : null);
        const hasVideo = !!video || isVideoExpected;

        if (hasVideo) {
            const fiberData = (video && (extractFromFiber(video) || extractFromFiber(video.parentElement))) || extractFromFiber(container);
            if (fiberData && fiberData.url && isLikelyVideoUrl(fiberData.url)) {
                return fiberData;
            }

            if (video) {
                const vSrc = video.src || video.currentSrc || '';
                if (vSrc && (vSrc.startsWith('http://') || vSrc.startsWith('https://')) && !vSrc.startsWith('blob:') && isLikelyVideoUrl(vSrc)) {
                    return { url: vSrc, isVideo: true, author: fiberData?.author, code: fiberData?.code };
                }
                const source = video.querySelector('source');
                if (source && source.src && (source.src.startsWith('http://') || source.src.startsWith('https://')) && !source.src.startsWith('blob:') && isLikelyVideoUrl(source.src)) {
                    return { url: source.src, isVideo: true, author: fiberData?.author, code: fiberData?.code };
                }
            }

            const ogVideo = document.querySelector('meta[property="og:video"], meta[property="og:video:secure_url"]');
            if (ogVideo && ogVideo.content && (ogVideo.content.startsWith('http://') || ogVideo.content.startsWith('https://')) && isLikelyVideoUrl(ogVideo.content)) {
                return { url: ogVideo.content, isVideo: true, author: fiberData?.author, code: fiberData?.code };
            }

            // Do not fall back to photo stills when expecting video.
            if (fiberData && (fiberData.code || fiberData.author)) {
                return { url: '', isVideo: true, code: fiberData.code, author: fiberData.author };
            }
            return null;
        }

        const fiberContainer = extractFromFiber(container);
        if (fiberContainer && fiberContainer.url && !isLikelyVideoUrl(fiberContainer.url)) {
            return fiberContainer;
        }

        const imgs = Array.from(container.querySelectorAll('img')).filter(img => {
            const r = img.getBoundingClientRect();
            return r.width > 120 && r.height > 120;
        });
        const mainImg = imgs[0];
        if (mainImg) {
            if (mainImg.srcset) {
                const parts = mainImg.srcset.split(',').map(s => s.trim().split(/\s+/));
                const best = parts[parts.length - 1]?.[0];
                if (best && (best.startsWith('http://') || best.startsWith('https://'))) {
                    return { url: best.replace(/&amp;/g, '&'), isVideo: false, code: fiberContainer?.code, author: fiberContainer?.author };
                }
            }
            const iSrc = mainImg.currentSrc || mainImg.src;
            if (iSrc && (iSrc.startsWith('http://') || iSrc.startsWith('https://'))) {
                return { url: iSrc.replace(/&amp;/g, '&'), isVideo: false, code: fiberContainer?.code, author: fiberContainer?.author };
            }
        }

        const ogImage = document.querySelector('meta[property="og:image"]');
        if (ogImage && ogImage.content && (ogImage.content.startsWith('http://') || ogImage.content.startsWith('https://'))) {
            return { url: ogImage.content, isVideo: false };
        }

        return null;
    }

    async function handleMediaDownload(postContainer, shortcode, activeVideo) {
        try {
            let slideIndex = 0;
            if (postContainer) {
                const activeStep = postContainer.querySelector('button[aria-current="step"]');
                if (activeStep && activeStep.parentElement) {
                    const steps = Array.from(activeStep.parentElement.querySelectorAll('button'));
                    const idx = steps.indexOf(activeStep);
                    if (idx >= 0) slideIndex = idx;
                }
            }

            const hasVideoElement = !!(activeVideo || (postContainer && (postContainer.querySelector('video') || postContainer.tagName === 'VIDEO')) || window.location.pathname.includes('/reel'));

            let resolvedUrl = '';
            let isVideo = hasVideoElement;
            let author = '';

            try {
                const harvest = (window.wrappedJSObject && window.wrappedJSObject.__fg_media_harvest) || window.__fg_media_harvest;
                if (harvest && shortcode && harvest[shortcode]) {
                    const cached = harvest[shortcode];
                    if (cached.author) author = cached.author;
                    if (cached.carousel && cached.carousel.length > 0 && cached.carousel[slideIndex]) {
                        const slide = cached.carousel[slideIndex];
                        if (hasVideoElement) {
                            if (slide.isVideo && slide.videoUrl && isLikelyVideoUrl(slide.videoUrl)) {
                                resolvedUrl = slide.videoUrl;
                                isVideo = true;
                            }
                        } else {
                            if (!slide.isVideo && slide.imageUrl && isLikelyImageUrl(slide.imageUrl)) {
                                resolvedUrl = slide.imageUrl;
                                isVideo = false;
                            } else if (slide.url && !isLikelyVideoUrl(slide.url)) {
                                resolvedUrl = slide.url;
                                isVideo = false;
                            }
                        }
                    } else {
                        if (hasVideoElement) {
                            if (cached.videoUrl && isLikelyVideoUrl(cached.videoUrl)) {
                                resolvedUrl = cached.videoUrl;
                                isVideo = true;
                            }
                        } else {
                            if (cached.imageUrl && isLikelyImageUrl(cached.imageUrl)) {
                                resolvedUrl = cached.imageUrl;
                                isVideo = false;
                            } else if (cached.url && !isLikelyVideoUrl(cached.url)) {
                                resolvedUrl = cached.url;
                                isVideo = false;
                            }
                        }
                    }
                }
            } catch (_) {}

            if (!resolvedUrl && hasVideoElement) {
                const streamMedia = getActiveStreamFromPerformance();
                if (streamMedia && streamMedia.url && isLikelyVideoUrl(streamMedia.url)) {
                    resolvedUrl = streamMedia.url;
                    isVideo = true;
                }
            }

            if (!resolvedUrl) {
                try {
                    const recents = (window.wrappedJSObject && window.wrappedJSObject.__fg_recent_media) || window.__fg_recent_media;
                    if (recents && recents.length > 0) {
                        const authorEl = postContainer ? (postContainer.querySelector('header a, a[role="link"]:not([href*="audio"]):not([href*="explore"]):not([href*="/p/"]):not([href*="/reel/"])')) : null;
                        const cardAuthor = (authorEl?.textContent || '').trim().toLowerCase().replace('@', '');
                        if (cardAuthor) {
                            const match = recents.find(r => (r.author || '').toLowerCase() === cardAuthor);
                            if (match) {
                                if (!author && match.author) author = match.author;
                                if (hasVideoElement && match.videoUrl && isLikelyVideoUrl(match.videoUrl)) {
                                    resolvedUrl = match.videoUrl;
                                    isVideo = true;
                                } else if (!hasVideoElement && (match.imageUrl || match.url)) {
                                    const candidate = match.imageUrl || match.url;
                                    if (!isLikelyVideoUrl(candidate)) {
                                        resolvedUrl = candidate;
                                        isVideo = false;
                                    }
                                }
                                if (!shortcode && match.code) shortcode = match.code;
                            }
                        }
                        if (!resolvedUrl && (window.location.pathname.includes('/reels') || window.location.pathname.includes('/reel/'))) {
                            const top = recents[0];
                            if (top) {
                                if (!author && top.author) author = top.author;
                                if (hasVideoElement && top.videoUrl && isLikelyVideoUrl(top.videoUrl)) {
                                    resolvedUrl = top.videoUrl;
                                    isVideo = true;
                                } else if (!hasVideoElement && (top.imageUrl || top.url)) {
                                    const candidate = top.imageUrl || top.url;
                                    if (!isLikelyVideoUrl(candidate)) {
                                        resolvedUrl = candidate;
                                        isVideo = false;
                                    }
                                }
                                if (!shortcode && top.code) shortcode = top.code;
                            }
                        }
                    }
                } catch (_) {}
            }

            if (!resolvedUrl) {
                const domMedia = extractBestDomUrl(activeVideo || postContainer, hasVideoElement);
                if (domMedia && domMedia.url && domMedia.url.startsWith('http')) {
                    if (hasVideoElement && isLikelyVideoUrl(domMedia.url)) {
                        resolvedUrl = domMedia.url;
                        isVideo = true;
                    } else if (!hasVideoElement && !isLikelyVideoUrl(domMedia.url)) {
                        resolvedUrl = domMedia.url;
                        isVideo = false;
                    }
                }
                if (!shortcode && domMedia?.code) shortcode = domMedia.code;
                if (!author && domMedia?.author) author = domMedia.author;
            }

            if ((!resolvedUrl || (hasVideoElement && isLikelyImageUrl(resolvedUrl))) && shortcode) {
                const apiMedia = await fetchMediaByShortcode(shortcode, slideIndex);
                if (apiMedia && apiMedia.url && apiMedia.url.startsWith('http')) {
                    if (hasVideoElement) {
                        const targetVid = (apiMedia.videoUrl && isLikelyVideoUrl(apiMedia.videoUrl)) ? apiMedia.videoUrl : (isLikelyVideoUrl(apiMedia.url) ? apiMedia.url : '');
                        if (targetVid) {
                            resolvedUrl = targetVid;
                            isVideo = true;
                        }
                    } else {
                        const targetImg = (apiMedia.imageUrl && isLikelyImageUrl(apiMedia.imageUrl)) ? apiMedia.imageUrl : (!isLikelyVideoUrl(apiMedia.url) ? apiMedia.url : '');
                        if (targetImg) {
                            resolvedUrl = targetImg;
                            isVideo = false;
                        }
                    }
                    if (!author && apiMedia.author) author = apiMedia.author;
                }
            }

            if (hasVideoElement && (!resolvedUrl || isLikelyImageUrl(resolvedUrl))) {
                const streamFallback = getActiveStreamFromPerformance();
                if (streamFallback && streamFallback.url && isLikelyVideoUrl(streamFallback.url)) {
                    resolvedUrl = streamFallback.url;
                    isVideo = true;
                }
            }

            if (!author && postContainer) {
                const authorEl = postContainer.querySelector('header a, a[role="link"]:not([href*="audio"]):not([href*="explore"]):not([href*="/p/"]):not([href*="/reel/"])');
                if (authorEl && authorEl.textContent) {
                    author = authorEl.textContent.trim().replace(/^@/, '');
                }
            }

            if (hasVideoElement && isLikelyImageUrl(resolvedUrl)) {
                resolvedUrl = '';
            }
            if (!hasVideoElement && isLikelyVideoUrl(resolvedUrl)) {
                resolvedUrl = '';
            }

            if (resolvedUrl && (resolvedUrl.startsWith('http://') || resolvedUrl.startsWith('https://') || resolvedUrl.startsWith('data:'))) {
                const cleanAuthor = author ? author.replace(/^@/, '').trim().slice(0, 13) : '';
                const authorPrefix = cleanAuthor ? ('@' + cleanAuthor) : '@Focusgram';
                const isFinalVideo = hasVideoElement && isVideo && !isLikelyImageUrl(resolvedUrl);
                const ext = isFinalVideo ? '.mp4' : '.jpg';
                const filename = `${authorPrefix} - Focusgram${slideIndex > 0 ? ('_' + (slideIndex + 1)) : ''}${ext}`;
                triggerNativeDownload(resolvedUrl, filename, null);
                return;
            }

            const errorMsg = hasVideoElement ? 'Could not extract MP4 video URL for this post' : 'Could not extract photo URL for this post';
            triggerNativeDownload(null, null, errorMsg);
        } catch (e) {
            console.error('[FocusGram Downloader] handleMediaDownload error:', e);
            triggerNativeDownload(null, null, 'Download error: ' + (e.message || e));
        }
    }

    async function handleProfilePictureDownload(username, avatarImg) {
        try {
            const harvested = (window.wrappedJSObject && window.wrappedJSObject.__fg_profile_pics) || window.__fg_profile_pics;
            if (harvested && username && harvested[username.toLowerCase()]) {
                const hdUrl = harvested[username.toLowerCase()];
                if (hdUrl && hdUrl.startsWith('http')) {
                    triggerNativeDownload(hdUrl, username + '_profile_pic_hd.jpg', null);
                    return;
                }
            }

            if (username) {
                try {
                    const res = await fetch('/api/v1/users/web_profile_info/?username=' + encodeURIComponent(username), {
                        headers: {
                            'x-ig-app-id': '936619743392459',
                            'x-requested-with': 'XMLHttpRequest'
                        },
                        credentials: 'include'
                    });
                    if (res.ok) {
                        const json = await res.json();
                        const hdUrl = json?.data?.user?.profile_pic_url_hd || json?.data?.user?.profile_pic_url;
                        if (hdUrl && hdUrl.startsWith('http')) {
                            triggerNativeDownload(hdUrl, username + '_profile_pic_hd.jpg', null);
                            return;
                        }
                    }
                } catch (e) {
                    console.warn('[FocusGram Downloader] HD profile pic API failed:', e);
                }
            }

            // Meta CDN URL parameters and path tokens are HMAC-signed; mutating them breaks signatures.
            if (avatarImg) {
                let url = '';
                if (avatarImg.srcset) {
                    const parts = avatarImg.srcset.split(',').map(s => s.trim().split(/\s+/));
                    url = parts[parts.length - 1]?.[0] || '';
                }
                if (!url) url = avatarImg.currentSrc || avatarImg.src || '';
                if (url && url.startsWith('http')) {
                    const cleanUrl = url.replace(/&amp;/g, '&');
                    triggerNativeDownload(cleanUrl, (username || 'instagram') + '_profile_pic.jpg', null);
                    return;
                }
            }

            triggerNativeDownload(null, null, 'Could not extract profile picture URL');
        } catch (e) {
            triggerNativeDownload(null, null, 'Profile download error: ' + (e.message || e));
        }
    }

    function syncProfileDownloadButton() {
        if (!isDownloadEnabled()) {
            document.querySelectorAll('[data-fg-profile-dl="1"]').forEach(el => el.remove());
            return;
        }

        const path = (window.location.pathname || '').replace(/^\/|\/$/g, '');
        if (!path) return;
        const segments = path.split('/').filter(Boolean);
        if (segments.length === 0) return;

        const RESERVED = ['explore', 'reels', 'reel', 'p', 'direct', 'stories', 'accounts', 'developer', 'about', 'legal', 'settings'];
        const usernameCandidate = segments[0];
        if (RESERVED.includes(usernameCandidate.toLowerCase())) return;
        const username = usernameCandidate;

        const avatarImgs = Array.from(document.querySelectorAll('header img, section header img, main header img, [role="main"] header img, img[alt*="profile picture" i], img[alt*="\'s profile" i]'))
            .filter(img => {
                const r = img.getBoundingClientRect();
                return r.width >= 40 && r.height >= 40;
            });
        const avatarImg = avatarImgs[0];
        if (!avatarImg) return;

        const avatarBtn = avatarImg.closest('button, [role="button"]') || avatarImg.parentElement;
        if (!avatarBtn) return;

        const wrapper = avatarBtn.parentElement || avatarBtn;
        if (wrapper.querySelector('[data-fg-profile-dl="1"]')) return;
        if (document.querySelector('[data-fg-profile-dl="1"]')) return;

        const pos = window.getComputedStyle(wrapper).position;
        if (!pos || pos === 'static') wrapper.style.position = 'relative';

        const dlBadge = document.createElement('div');
        dlBadge.setAttribute('data-fg-profile-dl', '1');
        dlBadge.setAttribute('role', 'button');
        dlBadge.setAttribute('tabindex', '0');
        dlBadge.setAttribute('aria-label', 'Download Profile Picture');
        dlBadge.title = 'Download Profile Picture';
        dlBadge.style.cssText = [
            'position:absolute',
            'bottom:-4px',
            'right:-4px',
            'width:36px',
            'height:36px',
            'border-radius:50%',
            'background:linear-gradient(135deg, #00C6FF 0%, #0072FF 100%)',
            'color:#ffffff',
            'border:2.5px solid #18181c',
            'display:flex',
            'align-items:center',
            'justify-content:center',
            'cursor:pointer',
            'z-index:9999',
            'box-shadow:0 3px 10px rgba(0,0,0,0.5)',
            'pointer-events:auto',
            'transition:transform 0.15s ease'
        ].join(';');

        dlBadge.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round" style="pointer-events:none;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;

        let lastTrigger = 0;
        const triggerDownload = (e) => {
            e.preventDefault();
            e.stopPropagation();
            const now = Date.now();
            if (now - lastTrigger < 1000) return;
            lastTrigger = now;
            dlBadge.style.transform = 'scale(0.85)';
            setTimeout(() => { dlBadge.style.transform = 'scale(1)'; }, 150);
            handleProfilePictureDownload(username, avatarImg);
        };

        dlBadge.addEventListener('click', triggerDownload, true);
        dlBadge.addEventListener('touchend', triggerDownload, true);
        dlBadge.addEventListener('touchstart', (e) => e.stopPropagation(), true);
        dlBadge.addEventListener('pointerdown', (e) => e.stopPropagation(), true);

        wrapper.appendChild(dlBadge);
    }

    function findActionRow(post) {
        if (!post) return null;

        const saveBtn = post.querySelector('button[aria-label*="Save" i], [role="button"][aria-label*="Save" i], button[aria-label*="Bookmark" i], [role="button"][aria-label*="Bookmark" i], svg[aria-label*="Save" i], svg[aria-label*="Bookmark" i], svg polygon, svg path[d^="M20 22"], svg path[d^="M20 21"]');
        if (saveBtn) {
            const btn = saveBtn.closest('button, [role="button"]') || saveBtn;
            if (btn && btn.parentElement) {
                return { row: btn.parentElement, anchor: btn, position: 'before' };
            }
        }

        const shareBtn = post.querySelector('button[aria-label*="Share" i], [role="button"][aria-label*="Share" i], svg[aria-label*="Share" i], svg path[d^="M13.973 20.046"], svg path[d*="M22 3L2 10"]');
        if (shareBtn) {
            const btn = shareBtn.closest('button, [role="button"]') || shareBtn;
            if (btn && btn.parentElement) {
                return { row: btn.parentElement, anchor: btn, position: 'after' };
            }
        }

        const commentBtn = post.querySelector('button[aria-label*="Comment" i], [role="button"][aria-label*="Comment" i], svg[aria-label*="Comment" i]');
        if (commentBtn) {
            const btn = commentBtn.closest('button, [role="button"]') || commentBtn;
            if (btn && btn.parentElement) {
                return { row: btn.parentElement, anchor: btn, position: 'after' };
            }
        }

        const likeBtn = post.querySelector('button[aria-label*="Like" i], [role="button"][aria-label*="Like" i], svg[aria-label*="Like" i], svg path[d*="M16.792 3.904"]');
        if (likeBtn) {
            const btn = likeBtn.closest('button, [role="button"]') || likeBtn;
            if (btn && btn.parentElement) {
                return { row: btn.parentElement, anchor: btn, position: 'after' };
            }
        }

        const section = post.querySelector('section');
        if (section) {
            const buttons = section.querySelectorAll('button, [role="button"]');
            if (buttons.length >= 1) {
                const lastBtn = buttons[buttons.length - 1];
                return { row: lastBtn.parentElement || section, anchor: lastBtn, position: 'after' };
            }
        }

        return null;
    }

    function syncDownloadButtons() {
        if (!isDownloadEnabled()) {
            document.querySelectorAll('[' + FG_DL_ATTR + '="1"]').forEach(btn => btn.remove());
            return;
        }

        const posts = [];
        const seen = new Set();

        document.querySelectorAll('article').forEach(el => {
            const r = el.getBoundingClientRect();
            if (r.height > 120 && r.width > 120 && !seen.has(el)) {
                seen.add(el);
                posts.push(el);
            }
        });

        posts.forEach(post => {
            if (post.querySelector('[' + FG_DL_ATTR + '="1"]')) return;

            const link = post.querySelector('a[href*="/p/"], a[href*="/reel/"], a[href*="/reels/"]');
            const href = link ? (link.getAttribute('href') || '') : '';
            const codeMatch = href.match(/\/(?:p|reel|reels)\/([A-Za-z0-9_-]+)/);
            const shortcode = codeMatch ? codeMatch[1] : '';

            const actionInfo = findActionRow(post);

            const dlBtn = document.createElement('div');
            dlBtn.className = 'fg-feed-download-btn';
            dlBtn.setAttribute(FG_DL_ATTR, '1');
            dlBtn.setAttribute('tabindex', '0');
            dlBtn.setAttribute('role', 'button');
            dlBtn.setAttribute('aria-label', 'Download Media');
            dlBtn.title = 'Download Media';

            let lastTrigger = 0;
            let touchStartX = 0;
            let touchStartY = 0;
            let touchStartTime = 0;
            let isHolding = false;
            let hasMoved = false;
            let holdThresholdPassed = false;
            let holdTimer = null;

            const executeDownload = () => {
                const now = Date.now();
                if (now - lastTrigger < 1000) return;
                lastTrigger = now;

                dlBtn.style.transform = 'scale(0.82)';
                setTimeout(() => { dlBtn.style.transform = 'scale(1)'; }, 150);

                let code = shortcode;
                if (!code) {
                    const l = post.querySelector('a[href*="/p/"], a[href*="/reel/"], a[href*="/reels/"]');
                    const h = l ? (l.getAttribute('href') || '') : '';
                    const m = h.match(/\/(?:p|reel|reels)\/([A-Za-z0-9_-]+)/);
                    if (m && m[1] !== 'p' && m[1] !== 'reel' && m[1] !== 'reels') {
                        code = m[1];
                    }
                }

                handleMediaDownload(post, code);
            };

            // Require stationary 30ms hold to prevent accidental triggers during fling scrolls.
            dlBtn.addEventListener('touchstart', (e) => {
                if (isGlobalScrolling || (Date.now() - lastScrollTime < 250)) {
                    isHolding = false;
                    hasMoved = true;
                    return;
                }
                if (!e.touches || e.touches.length === 0) return;
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
                touchStartTime = Date.now();
                isHolding = true;
                hasMoved = false;
                holdThresholdPassed = false;

                clearTimeout(holdTimer);
                holdTimer = setTimeout(() => {
                    if (isHolding && !hasMoved && !isGlobalScrolling && (Date.now() - lastScrollTime >= 250)) {
                        holdThresholdPassed = true;
                    }
                }, 30);
            }, { passive: true, capture: true });

            dlBtn.addEventListener('touchmove', (e) => {
                if (!isHolding || !e.touches || e.touches.length === 0) return;
                const dx = Math.abs(e.touches[0].clientX - touchStartX);
                const dy = Math.abs(e.touches[0].clientY - touchStartY);
                if (dx > 3 || dy > 3 || isGlobalScrolling) {
                    hasMoved = true;
                    isHolding = false;
                    clearTimeout(holdTimer);
                }
            }, { passive: true, capture: true });

            dlBtn.addEventListener('touchcancel', () => {
                isHolding = false;
                hasMoved = true;
                clearTimeout(holdTimer);
            }, { passive: true, capture: true });

            dlBtn.addEventListener('touchend', (e) => {
                e.preventDefault();
                e.stopPropagation();
                clearTimeout(holdTimer);
                const holdDuration = Date.now() - touchStartTime;

                if (isHolding && !hasMoved && (holdThresholdPassed || holdDuration >= 30) && !isGlobalScrolling && (Date.now() - lastScrollTime >= 250)) {
                    executeDownload();
                }

                isHolding = false;
                hasMoved = false;
                holdThresholdPassed = false;
            }, true);

            dlBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
            }, true);

            if (actionInfo && actionInfo.row && actionInfo.anchor) {
                dlBtn.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;cursor:pointer;padding:8px;margin-right:4px;color:currentColor;opacity:0.9;vertical-align:middle;box-sizing:border-box;pointer-events:auto;transition:transform 0.15s ease;';
                dlBtn.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;pointer-events:none;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;
                if (actionInfo.position === 'before') {
                    actionInfo.row.insertBefore(dlBtn, actionInfo.anchor);
                } else {
                    actionInfo.row.insertBefore(dlBtn, actionInfo.anchor.nextSibling || actionInfo.anchor);
                }
            } else {
                const section = post.querySelector('section');
                if (section) {
                    dlBtn.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;cursor:pointer;padding:8px;margin-right:4px;color:currentColor;opacity:0.9;vertical-align:middle;box-sizing:border-box;pointer-events:auto;';
                    dlBtn.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;pointer-events:none;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;
                    section.appendChild(dlBtn);
                } else {
                    const postMedia = post.querySelector('video')?.parentElement ||
                                      Array.from(post.querySelectorAll('img')).find(img => !img.closest('header') && !img.alt.includes('profile') && img.getBoundingClientRect().width > 120)?.parentElement ||
                                      post;
                    const pos = window.getComputedStyle(postMedia).position;
                    if (!pos || pos === 'static') postMedia.style.position = 'relative';

                    dlBtn.style.cssText = 'position:absolute;bottom:12px;right:12px;z-index:90;width:38px;height:38px;border-radius:50%;background:rgba(0,0,0,0.65);border:1px solid rgba(255,255,255,0.25);color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;backdrop-filter:blur(8px);box-shadow:0 2px 8px rgba(0,0,0,0.4);pointer-events:auto;transition:transform 0.15s ease;';
                    dlBtn.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="display:block;pointer-events:none;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;
                    postMedia.appendChild(dlBtn);
                }
            }
        });
    }

    function syncReelsDownloadButton() {
        // ReelsTopBar handles reel downloads natively; strip injected buttons.
        document.querySelectorAll('[data-fg-reel-dl="1"]').forEach(el => el.remove());
    }

    async function downloadActiveReel() {
        try {
            const vh = window.innerHeight;
            const videos = Array.from(document.querySelectorAll('video'));
            let activeVideo = null;
            let minCenterDist = Infinity;
            for (const v of videos) {
                const r = v.getBoundingClientRect();
                if (r.height > 100 && r.bottom > 0 && r.top < vh) {
                    const dist = Math.abs((r.top + r.height / 2) - vh / 2);
                    if (!v.paused) {
                        activeVideo = v;
                        break;
                    }
                    if (dist < minCenterDist) {
                        minCenterDist = dist;
                        activeVideo = v;
                    }
                }
            }
            if (!activeVideo && videos.length > 0) activeVideo = videos[0];

            const card = activeVideo ? (activeVideo.closest('[data-fg-reel-card]') || activeVideo.closest('article') || activeVideo.parentElement) : document;

            let code = null;
            if (card) {
                const reelLink = card.querySelector('a[href*="/reel/"], a[href*="/p/"], a[href*="/reels/"]');
                if (reelLink && reelLink.href) {
                    const m = reelLink.href.match(/\/(?:reel|reels|p)\/([A-Za-z0-9_-]{4,})/);
                    if (m && m[1] !== 'reels' && m[1] !== 'reel') code = m[1];
                }
            }
            if (!code) {
                const m = window.location.pathname.match(/\/(?:reel|reels|p)\/([A-Za-z0-9_-]{4,})/);
                if (m && m[1] !== 'reels' && m[1] !== 'reel') code = m[1];
            }

            if (!code && (activeVideo || card)) {
                const fiberData = extractFromFiber(activeVideo) || extractFromFiber(card);
                if (fiberData && fiberData.code) {
                    code = fiberData.code;
                }
            }

            await handleMediaDownload(card, code, activeVideo);
        } catch (e) {
            console.error('[FocusGram Downloader] downloadActiveReel error:', e);
            triggerNativeDownload(null, null, 'Download error: ' + (e.message || e));
        }
    }

    window.__fgDownloadActiveReel = downloadActiveReel;
    window.addEventListener('focusgram:download-reel', downloadActiveReel);

    window.addEventListener('message', (e) => {
        if (e.data && e.data.type === 'FOCUSGRAM_UPDATE' && e.data.config) {
            syncDownloadButtons();
            syncProfileDownloadButton();
            syncReelsDownloadButton();
        }
    });

    setInterval(() => {
        syncDownloadButtons();
        syncProfileDownloadButton();
        syncReelsDownloadButton();
    }, 300);

    setTimeout(() => {
        syncDownloadButtons();
        syncProfileDownloadButton();
        syncReelsDownloadButton();
    }, 200);

})();
