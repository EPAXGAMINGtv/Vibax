let currentUser = null;
let feedVideos = [];
let currentVideoId = null;
let viewedVideos = new Set();

document.addEventListener('DOMContentLoaded', init);

/* ── SVG Icon Helpers ── */
function svgIcon(path, clazz = 'icon') {
    return `<svg class="${clazz}" viewBox="0 0 24 24"><path d="${path}"/></svg>`;
}
function heartIcon(filled) { return filled
    ? '<svg class="icon" viewBox="0 0 24 24" fill="currentColor"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>'
    : '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>';
}
function commentIcon() { return '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>'; }
function repostIcon() { return '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>'; }
function shareIcon() { return '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="4" y1="12" x2="20" y2="12"/><polyline points="14 6 20 12 14 18"/></svg>'; }
function starIcon(filled) { return filled
    ? '<svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>'
    : '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>';
}
function soundIcon(on) { return on
    ? '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>'
    : '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/></svg>';
}
function playIcon() { return '<svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>'; }
function sendIcon() { return '<svg class="icon icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>'; }
function checkIcon() { return '<svg class="icon icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'; }

/* ── Init ── */
async function init() {
    setupAuth();
    setupNavigation();
    setupSearch();
    setupUpload();
    setupModals();

    if (API.token) {
        try {
            currentUser = await API.me();
            showMain();
            loadFeed();
        } catch {
            API.setToken(null);
            showAuth();
        }
    } else {
        showAuth();
    }
}

/* ── Auth ── */
function setupAuth() {
    document.querySelectorAll('.auth-tabs .tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.auth-tabs .tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
            tab.classList.add('active');
            document.getElementById(tab.dataset.tab + '-form').classList.add('active');
        });
    });

    document.getElementById('login-form').addEventListener('submit', async e => {
        e.preventDefault();
        const err = document.getElementById('auth-error');
        try {
            const data = await API.login(
                document.getElementById('login-user').value,
                document.getElementById('login-pass').value
            );
            API.setToken(data.token);
            currentUser = await API.me();
            showMain();
            loadFeed();
            err.textContent = '';
        } catch (ex) {
            err.textContent = ex.message;
        }
    });

    document.getElementById('register-form').addEventListener('submit', async e => {
        e.preventDefault();
        const err = document.getElementById('auth-error');
        try {
            const data = await API.register(
                document.getElementById('reg-user').value,
                document.getElementById('reg-name').value,
                document.getElementById('reg-pass').value
            );
            API.setToken(data.token);
            currentUser = await API.me();
            showMain();
            loadFeed();
            err.textContent = '';
        } catch (ex) {
            err.textContent = ex.message;
        }
    });
}

function showAuth() {
    document.getElementById('auth-screen').classList.add('active');
    document.getElementById('main-screen').classList.remove('active');
}

function showMain() {
    document.getElementById('auth-screen').classList.remove('active');
    document.getElementById('main-screen').classList.add('active');
    startPolling();
}

let pollInterval = null;

function startPolling() {
    if (pollInterval) return;
    pollInterval = setInterval(pollUnread, 3000);
}

function stopPolling() {
    if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
    }
}

async function pollUnread() {
    try {
        const data = await API.getUnread();
        const total = (data.messages || 0) + (data.requests || 0);
        const badge = document.getElementById('inbox-badge');
        if (total > 0) {
            badge.textContent = total > 99 ? '99+' : total;
            badge.classList.remove('hidden');
        } else {
            badge.classList.add('hidden');
        }
    } catch {}
}

/* ── Navigation ── */
function setupNavigation() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const view = btn.dataset.view;
            if (!view) return;

            document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
            document.getElementById('view-' + view).classList.add('active');

            if (view === 'feed') loadFeed();
            if (view === 'profile') loadProfile();
            if (view === 'inbox') loadInbox();
        });
    });

    document.getElementById('back-from-user').addEventListener('click', () => {
        document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
        document.getElementById('view-feed').classList.add('active');
        document.querySelector('[data-view="feed"]').classList.add('active');
    });

    // Inbox tabs - setup once
    document.querySelectorAll('.inbox-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.inbox-tab').forEach(t => {
                t.classList.remove('active');
                t.style.color = 'var(--text-muted)';
                t.style.borderBottomColor = 'transparent';
            });
            tab.classList.add('active');
            tab.style.color = 'var(--text)';
            tab.style.borderBottomColor = 'var(--primary)';
            document.getElementById('inbox-activity').style.display = tab.dataset.ibt === 'activity' ? 'block' : 'none';
            document.getElementById('inbox-friends').style.display = tab.dataset.ibt === 'friends' ? 'block' : 'none';
        });
    });
}

/* ── Feed ── */
let feedOffset = 0;
let feedLoading = false;
let feedHasMore = true;
let feedObserver = null;
const FEED_PAGE_SIZE = 10;

async function loadFeed() {
    const container = document.getElementById('feed-container');
    container.innerHTML = '<div class="empty-state">Lade Feed...</div>';
    feedOffset = 0;
    feedHasMore = true;
    feedVideos = [];

    try {
        const data = await API.getFeed(FEED_PAGE_SIZE, 0);
        feedVideos = data.videos || [];
        feedHasMore = data.hasMore === 'true';
        feedOffset = feedVideos.length;

        if (feedVideos.length === 0) {
            container.innerHTML = '<div class="empty-state">Noch keine Videos. Sei der Erste!</div>';
            return;
        }

        container.innerHTML = '';
        feedVideos.forEach((video, i) => {
            container.appendChild(createFeedItem(video, i));
        });

        setupFeedObserver();
        setupInfiniteScroll();
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">Fehler: ${ex.message}</div>`;
    }
}

async function loadMoreFeed() {
    if (feedLoading || !feedHasMore) return;
    feedLoading = true;
    try {
        const data = await API.getFeed(FEED_PAGE_SIZE, feedOffset);
        const newVideos = data.videos || [];
        feedHasMore = data.hasMore === 'true';
        feedOffset += newVideos.length;

        const container = document.getElementById('feed-container');
        newVideos.forEach((video, i) => {
            container.appendChild(createFeedItem(video, feedVideos.length + i));
        });
        feedVideos = feedVideos.concat(newVideos);
        setupFeedObserver();
    } catch (ex) {
        // silently fail
    } finally {
        feedLoading = false;
    }
}

let feedScrollHandler = null;

function setupInfiniteScroll() {
    const container = document.getElementById('feed-container');
    if (feedScrollHandler) container.removeEventListener('scroll', feedScrollHandler);
    feedScrollHandler = () => {
        if (container.scrollTop + container.clientHeight >= container.scrollHeight - 300) {
            loadMoreFeed();
        }
    };
    container.addEventListener('scroll', feedScrollHandler);
}

function isFollowing(author) {
    if (!currentUser || !currentUser.followingList) return false;
    return currentUser.followingList.includes(author);
}

function createFeedItem(video, index) {
    const item = document.createElement('div');
    item.className = 'feed-item';
    item.dataset.videoId = video.id;
    item.dataset.index = index;

    const isImage = video.mediaType === 'image';
    const mediaEl = isImage
        ? `<img class="feed-media" src="${API.mediaUrl(video.mediaPath)}" alt="${video.title}">`
        : `<video class="feed-media" src="${API.mediaUrl(video.mediaPath)}" loop playsinline preload="auto"></video>`;

    const liked = currentUser && currentUser.likes && currentUser.likes.includes(video.id);
    const reposted = currentUser && currentUser.reposts && currentUser.reposts.includes(video.id);
    const favorited = currentUser && currentUser.favorites && currentUser.favorites.includes(video.id);
    const following = currentUser && currentUser.username !== video.authorUsername && isFollowing(video.authorUsername);

    const followBtn = currentUser && currentUser.username !== video.authorUsername
        ? `<button class="follow-inline ${following ? 'following' : ''}" data-follow="${video.authorUsername}">${following ? 'Folge ich' : 'Folgen'}</button>`
        : '';

    item.innerHTML = `
        ${mediaEl}
        <div class="feed-overlay">
            <div class="feed-author" data-user="${video.authorUsername}">@${video.authorUsername}${followBtn}</div>
            <div class="feed-title">${esc(video.title)}</div>
            <div class="feed-desc">${esc(video.description || '')}</div>
            <div class="feed-tags">${(video.tags || []).map(t => '#' + t).join(' ')}</div>
        </div>
        <div class="feed-actions">
            <div class="feed-avatar" data-user="${video.authorUsername}">${video.authorUsername[0].toUpperCase()}</div>
            <button class="action-btn like-btn ${liked ? 'liked' : ''}" data-id="${video.id}" data-count="${video.likes}">
                <span class="icon-wrap">${heartIcon(liked)}</span>
                <span class="count">${formatCount(video.likes)}</span>
            </button>
            <button class="action-btn comment-btn" data-id="${video.id}">
                <span class="icon-wrap">${commentIcon()}</span>
                <span class="count">${formatCount(video.comments)}</span>
            </button>
            <button class="action-btn repost-btn ${reposted ? 'liked' : ''}" data-id="${video.id}" data-count="${video.reposts}">
                <span class="icon-wrap">${repostIcon()}</span>
                <span class="count">${formatCount(video.reposts)}</span>
            </button>
            <button class="action-btn share-btn" data-id="${video.id}">
                <span class="icon-wrap">${shareIcon()}</span>
            </button>
            ${currentUser ? `<button class="action-btn favorite-btn ${favorited ? 'liked' : ''}" data-id="${video.id}">
                <span class="icon-wrap">${starIcon(favorited)}</span>
            </button>` : ''}
            ${isImage ? '' : `<button class="action-btn sound-btn" id="sound-${video.id}"><span class="icon-wrap">${soundIcon(true)}</span></button>`}
        </div>
    `;

    item.querySelector('.like-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        toggleLike(video.id, item);
    });
    item.querySelector('.comment-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        openComments(video.id);
    });
    const repostBtn = item.querySelector('.repost-btn');
    if (repostBtn) {
        repostBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleRepost(video.id, item);
        });
    }
    const favBtn = item.querySelector('.favorite-btn');
    if (favBtn) {
        favBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleFavorite(video.id, item);
        });
    }
    item.querySelector('.share-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        openShare(video.id);
    });
    item.querySelectorAll('[data-user]').forEach(el => {
        el.addEventListener('click', (e) => {
            e.stopPropagation();
            showUserProfile(el.dataset.user);
        });
    });

    const followBtnEl = item.querySelector('[data-follow]');
    if (followBtnEl) {
        followBtnEl.addEventListener('click', async (e) => {
            e.stopPropagation();
            const username = followBtnEl.dataset.follow;
            try {
                if (followBtnEl.classList.contains('following')) {
                    await API.unfollow(username);
                    followBtnEl.classList.remove('following');
                    followBtnEl.textContent = 'Folgen';
                    if (currentUser.followingList) {
                        currentUser.followingList = currentUser.followingList.filter(u => u !== username);
                    }
                } else {
                    await API.follow(username);
                    followBtnEl.classList.add('following');
                    followBtnEl.textContent = 'Folge ich';
                    if (currentUser.followingList) {
                        currentUser.followingList.push(username);
                    }
                }
            } catch (ex) {
                if (ex.message !== 'Unauthorized') alert(ex.message);
            }
        });
    }

    const mediaElClick = item.querySelector('.feed-media');
    if (mediaElClick) {
        let clickTimer = null;
        mediaElClick.addEventListener('click', (e) => {
            e.stopPropagation();
            if (clickTimer) {
                clearTimeout(clickTimer);
                clickTimer = null;
                toggleLike(video.id, item);
            } else {
                clickTimer = setTimeout(() => {
                    clickTimer = null;
                    toggleMute(item);
                }, 250);
            }
        });
    }

    const soundBtn = item.querySelector('.sound-btn');
    if (soundBtn) {
        soundBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleMute(item);
        });
    }

    const videoEl = item.querySelector('video');
    if (videoEl) videoEl.load();

    return item;
}

function toggleMute(item) {
    const video = item.querySelector('video');
    if (!video) return;
    video.muted = !video.muted;
    const soundBtn = item.querySelector('.sound-btn');
    if (soundBtn) {
        soundBtn.querySelector('.icon-wrap').innerHTML = soundIcon(!video.muted);
    }
}

function setupFeedObserver() {
    if (feedObserver) feedObserver.disconnect();
    const container = document.getElementById('feed-container');
    feedObserver = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const videoId = entry.target.dataset.videoId;
                const video = entry.target.querySelector('video');
                if (video) {
                    if (video.readyState >= 3) {
                        video.play().catch(() => {});
                    } else {
                        const playWhenReady = () => {
                            video.play().catch(() => {});
                            video.removeEventListener('canplay', playWhenReady);
                        };
                        video.addEventListener('canplay', playWhenReady);
                        video.load();
                    }
                }
                if (!viewedVideos.has(videoId)) {
                    viewedVideos.add(videoId);
                    API.watch(videoId).catch(() => {});
                }
            } else {
                const video = entry.target.querySelector('video');
                if (video) video.pause();
            }
        });
    }, { threshold: 0.6 });

    container.querySelectorAll('.feed-item').forEach(item => feedObserver.observe(item));
}

async function toggleLike(videoId, item) {
    if (!currentUser) return;
    const btn = item.querySelector('.like-btn');
    if (!btn) return;
    const countEl = btn.querySelector('.count');
    const iconWrap = btn.querySelector('.icon-wrap');
    try {
        if (btn.classList.contains('liked')) {
            await API.unlike(videoId);
            btn.classList.remove('liked');
            let c = parseInt(btn.dataset.count) || 0;
            c = Math.max(0, c - 1);
            btn.dataset.count = c;
            countEl.textContent = formatCount(c);
            iconWrap.innerHTML = heartIcon(false);
            if (currentUser.likes) currentUser.likes = currentUser.likes.filter(id => id !== videoId);
        } else {
            await API.like(videoId);
            btn.classList.add('liked');
            let c = parseInt(btn.dataset.count) || 0;
            c = c + 1;
            btn.dataset.count = c;
            countEl.textContent = formatCount(c);
            iconWrap.innerHTML = heartIcon(true);
            if (currentUser.likes) currentUser.likes.push(videoId);
        }
    } catch (ex) {
        if (ex.message !== 'Unauthorized') alert(ex.message);
    }
}

async function toggleRepost(videoId, item) {
    if (!currentUser) return;
    const btn = item.querySelector('.repost-btn');
    if (!btn) return;
    const countEl = btn.querySelector('.count');
    try {
        if (btn.classList.contains('liked')) {
            await API.unrepost(videoId);
            btn.classList.remove('liked');
            let c = parseInt(btn.dataset.count) || 0;
            c = Math.max(0, c - 1);
            btn.dataset.count = c;
            countEl.textContent = formatCount(c);
            if (currentUser.reposts) currentUser.reposts = currentUser.reposts.filter(id => id !== videoId);
        } else {
            await API.repost(videoId);
            btn.classList.add('liked');
            let c = parseInt(btn.dataset.count) || 0;
            c = c + 1;
            btn.dataset.count = c;
            countEl.textContent = formatCount(c);
            if (currentUser.reposts) currentUser.reposts.push(videoId);
        }
    } catch (ex) {
        if (ex.message !== 'Unauthorized') alert(ex.message);
    }
}

async function toggleFavorite(videoId, item) {
    if (!currentUser) return;
    const btn = item.querySelector('.favorite-btn');
    if (!btn) return;
    const iconWrap = btn.querySelector('.icon-wrap');
    try {
        if (btn.classList.contains('liked')) {
            await API.unfavorite(videoId);
            btn.classList.remove('liked');
            iconWrap.innerHTML = starIcon(false);
            if (currentUser.favorites) currentUser.favorites = currentUser.favorites.filter(id => id !== videoId);
        } else {
            await API.favorite(videoId);
            btn.classList.add('liked');
            iconWrap.innerHTML = starIcon(true);
            if (currentUser.favorites) currentUser.favorites.push(videoId);
        }
    } catch (ex) {
        if (ex.message !== 'Unauthorized') alert(ex.message);
    }
}

/* ── Modals ── */
function setupModals() {
    document.getElementById('close-comments').addEventListener('click', () => {
        document.getElementById('comments-modal').classList.add('hidden');
    });
    document.getElementById('close-share').addEventListener('click', () => {
        document.getElementById('share-modal').classList.add('hidden');
    });
    document.getElementById('close-chat').addEventListener('click', () => {
        document.getElementById('chat-modal').classList.add('hidden');
        if (chatPollInterval) clearInterval(chatPollInterval);
        chatPollInterval = null;
    });
    document.getElementById('comment-submit').addEventListener('click', submitComment);
    document.getElementById('comment-text').addEventListener('keypress', e => {
        if (e.key === 'Enter') submitComment();
    });
    document.getElementById('copy-link').addEventListener('click', () => {
        navigator.clipboard.writeText(document.getElementById('share-link').textContent);
        showToast('Link kopiert!');
    });
    document.getElementById('chat-send').addEventListener('click', sendChatMessage);
    document.getElementById('chat-text').addEventListener('keypress', e => {
        if (e.key === 'Enter') sendChatMessage();
    });

    document.getElementById('comments-modal').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) document.getElementById('comments-modal').classList.add('hidden');
    });
    document.getElementById('share-modal').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) document.getElementById('share-modal').classList.add('hidden');
    });
    document.getElementById('chat-modal').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) {
            document.getElementById('chat-modal').classList.add('hidden');
            if (chatPollInterval) clearInterval(chatPollInterval);
            chatPollInterval = null;
        }
    });
}

/* ── Comments ── */
async function openComments(videoId) {
    if (!currentUser) return showToast('Bitte anmelden');
    currentVideoId = videoId;
    document.getElementById('comments-modal').classList.remove('hidden');
    const list = document.getElementById('comments-list');
    list.innerHTML = 'Lade...';
    try {
        const data = await API.getComments(videoId);
        if (!data.comments || data.comments.length === 0) {
            list.innerHTML = '<div class="empty-state">Noch keine Kommentare</div>';
            return;
        }
        list.innerHTML = data.comments.map(c => `
            <div class="comment-item">
                <div class="comment-author">@${esc(c.authorUsername)}</div>
                <div class="comment-text">${esc(c.text)}</div>
                <div class="comment-time">${timeAgo(c.createdAt)}</div>
            </div>
        `).join('');
    } catch (ex) {
        list.innerHTML = `<div class="empty-state">${ex.message}</div>`;
    }
}

async function submitComment() {
    const text = document.getElementById('comment-text').value.trim();
    if (!text || !currentVideoId) return;
    try {
        await API.addComment(currentVideoId, text);
        document.getElementById('comment-text').value = '';
        openComments(currentVideoId);
    } catch (ex) {
        alert(ex.message);
    }
}

/* ── Share ── */
let currentShareVideoId = null;

async function openShare(videoId) {
    currentShareVideoId = videoId;
    try {
        await API.share(videoId);
        const link = `${window.location.origin}/?video=${videoId}`;
        document.getElementById('share-link').textContent = link;
        const friendsContainer = document.getElementById('share-friends-list');
        if (currentUser && currentUser.friendsList && currentUser.friendsList.length) {
            friendsContainer.innerHTML = currentUser.friendsList.map(f =>
                `<div class="share-friend-item" data-friend="${esc(f)}">
                    <div class="avatar-sm">${f[0].toUpperCase()}</div>
                    <span>@${esc(f)}</span>
                </div>`
            ).join('');
            friendsContainer.querySelectorAll('.share-friend-item').forEach(el => {
                el.addEventListener('click', () => shareToFriend(videoId, el.dataset.friend));
            });
        } else {
            friendsContainer.innerHTML = '<div style="color:var(--text-muted);font-size:0.85rem;padding:8px;">Keine Freunde zum Teilen</div>';
        }
        document.getElementById('share-modal').classList.remove('hidden');
    } catch (ex) {
        alert(ex.message);
    }
}

async function shareToFriend(videoId, friend) {
    try {
        await API.shareToFriend(videoId, friend);
        document.getElementById('share-modal').classList.add('hidden');
        showToast('Video an @' + friend + ' gesendet!');
    } catch (ex) {
        alert(ex.message);
    }
}

/* ── Search ── */
function setupSearch() {
    let timeout;
    document.getElementById('search-input').addEventListener('input', e => {
        clearTimeout(timeout);
        timeout = setTimeout(() => doSearch(e.target.value), 300);
    });
    loadTagCategories();
}

let activeTagFilter = '';

async function loadTagCategories() {
    try {
        const data = await API.getHashtags();
        const tags = data.hashtags || [];
        const container = document.getElementById('search-tags');
        if (tags.length === 0) { container.innerHTML = ''; return; }
        container.innerHTML = '<span class="tag-chip' + (activeTagFilter === '' ? ' active' : '') + '" data-tag="">Alle</span>' +
            tags.slice(0, 15).map(t => `<span class="tag-chip${activeTagFilter === t ? ' active' : ''}" data-tag="${esc(t)}">#${esc(t)}</span>`).join('');
        container.querySelectorAll('.tag-chip').forEach(el => {
            el.addEventListener('click', () => {
                activeTagFilter = el.dataset.tag;
                container.querySelectorAll('.tag-chip').forEach(c => c.classList.remove('active'));
                el.classList.add('active');
                const q = document.getElementById('search-input').value.trim();
                if (q) doSearch(q);
                else searchByTag(activeTagFilter);
            });
        });
    } catch {}
}

async function searchByTag(tag) {
    const container = document.getElementById('search-results');
    container.innerHTML = '<div class="empty-state">Suche...</div>';
    try {
        const videos = tag ? await API.searchVideosByTag(tag) : await API.searchVideos('');
        let html = '';
        if (videos.videos && videos.videos.length) {
            html += '<div class="search-section"><h3>Videos mit #' + esc(tag) + '</h3>';
            html += videos.videos.map(v => `
                <div class="search-video" data-video="${v.id}">
                    <video class="search-video-thumb" src="${API.mediaUrl(v.mediaPath)}" preload="auto" muted playsinline></video>
                    <div><strong>${esc(v.title)}</strong><br><small>@${esc(v.authorUsername)} <span style="color:var(--secondary)">${(v.tags||[]).map(t => '#'+t).join(' ')}</span></small></div>
                </div>
            `).join('');
        }
        container.innerHTML = html || '<div class="empty-state">Keine Videos mit diesem Tag</div>';
        container.querySelectorAll('[data-video]').forEach(el => {
            el.addEventListener('click', () => openVideoPreview(el.dataset.video));
        });
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">${ex.message}</div>`;
    }
}

async function doSearch(query) {
    const container = document.getElementById('search-results');
    if (!query.trim()) {
        if (activeTagFilter) { searchByTag(activeTagFilter); return; }
        container.innerHTML = '';
        return;
    }

    container.innerHTML = '<div class="empty-state">Suche...</div>';
    try {
        const [users, videos] = await Promise.all([
            API.searchUsers(query),
            API.searchVideos(query)
        ]);

        let html = '';
        if (users.users && users.users.length) {
            html += '<div class="search-section"><h3>Benutzer</h3>';
            html += users.users.map(u => `
                <div class="search-user" data-user="${u.username}">
                    <div class="avatar-sm">${u.username[0].toUpperCase()}</div>
                    <div><strong>@${esc(u.username)}</strong><br><small>${esc(u.visibleName)}</small></div>
                </div>
            `).join('');
            html += '</div>';
        }
        if (videos.videos && videos.videos.length) {
            html += '<div class="search-section"><h3>Videos</h3>';
            html += videos.videos.map(v => `
                <div class="search-video" data-video="${v.id}">
                    <video class="search-video-thumb" src="${API.mediaUrl(v.mediaPath)}" preload="auto" muted playsinline></video>
                    <div><strong>${esc(v.title)}</strong><br><small>@${esc(v.authorUsername)} <span style="color:var(--secondary)">${(v.tags||[]).map(t => '#'+t).join(' ')}</span></small></div>
                </div>
            `).join('');
            html += '</div>';
        }
        container.innerHTML = html || '<div class="empty-state">Keine Ergebnisse</div>';

        container.querySelectorAll('[data-user]').forEach(el => {
            el.addEventListener('click', () => showUserProfile(el.dataset.user));
        });
        container.querySelectorAll('[data-video]').forEach(el => {
            el.addEventListener('click', () => openVideoPreview(el.dataset.video));
        });
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">${ex.message}</div>`;
    }
}

/* ── Upload ── */
function setupUpload() {
    const tagInput = document.getElementById('upload-tags');
    const suggestions = document.getElementById('tag-suggestions');

    tagInput.addEventListener('input', async () => {
        const val = tagInput.value;
        const lastTag = val.split(',').pop().trim().toLowerCase();
        if (lastTag.length < 1) {
            suggestions.classList.add('hidden');
            return;
        }
        try {
            const data = await API.getHashtags(lastTag);
            const filtered = (data.hashtags || []).filter(t => t !== lastTag).slice(0, 5);
            if (filtered.length === 0) {
                suggestions.classList.add('hidden');
                return;
            }
            suggestions.innerHTML = filtered.map(t =>
                `<div class="tag-suggestion" data-tag="${t}">#${esc(t)}</div>`
            ).join('');
            suggestions.classList.remove('hidden');
            suggestions.querySelectorAll('.tag-suggestion').forEach(el => {
                el.addEventListener('click', () => {
                    const parts = val.split(',');
                    parts[parts.length - 1] = el.dataset.tag;
                    tagInput.value = parts.join(',') + ',';
                    tagInput.focus();
                    suggestions.classList.add('hidden');
                });
            });
        } catch { /* ignore */ }
    });

    tagInput.addEventListener('blur', () => {
        setTimeout(() => suggestions.classList.add('hidden'), 200);
    });

    document.getElementById('upload-file').addEventListener('change', () => {
        const file = document.getElementById('upload-file').files[0];
        if (file) {
            document.getElementById('progress-text').textContent = 'Datei ausgewaehlt: ' + file.name;
        }
    });

    document.getElementById('upload-btn').addEventListener('click', async () => {
        if (!currentUser) return showToast('Bitte anmelden');

        const title = document.getElementById('upload-title').value.trim();
        const desc = document.getElementById('upload-desc').value.trim();
        const tags = document.getElementById('upload-tags').value.trim();
        const file = document.getElementById('upload-file').files[0];

        if (!title) return showToast('Titel erforderlich');

        const progressBar = document.getElementById('upload-progress');
        const progressFill = document.getElementById('progress-fill');
        const progressText = document.getElementById('progress-text');
        const uploadBtn = document.getElementById('upload-btn');

        try {
            let mediaPath = '';
            let mediaType = 'video';

            if (file) {
                progressBar.classList.remove('hidden');
                uploadBtn.disabled = true;
                uploadBtn.textContent = 'Upload lauft...';

                const base64 = await fileToBase64(file);
                const upload = await API.uploadMediaWithProgress(base64, file.name, (pct) => {
                    progressFill.style.width = pct + '%';
                    progressText.textContent = pct + '%';
                });
                mediaPath = upload.path;
                mediaType = file.type.startsWith('image/') ? 'image' : 'video';
            }

            progressText.textContent = 'Video wird erstellt...';
            await API.createVideo({ title, description: desc, tags, mediaPath, mediaType });

            progressFill.style.width = '100%';
            progressText.textContent = 'Fertig!';
            setTimeout(() => {
                document.getElementById('upload-title').value = '';
                document.getElementById('upload-desc').value = '';
                document.getElementById('upload-tags').value = '';
                document.getElementById('upload-file').value = '';
                progressBar.classList.add('hidden');
                progressFill.style.width = '0%';
                progressText.textContent = '0%';
                uploadBtn.disabled = false;
                uploadBtn.textContent = 'Posten';
                document.querySelector('[data-view="feed"]').click();
            }, 500);
        } catch (ex) {
            progressBar.classList.add('hidden');
            progressFill.style.width = '0%';
            uploadBtn.disabled = false;
            uploadBtn.textContent = 'Posten';
            alert('Fehler: ' + ex.message);
        }
    });
}

/* ── Profile ── */
let profileTab = 'videos';

async function loadProfile() {
    if (!currentUser) return;
    currentUser = await API.me();

    const header = document.getElementById('profile-header');
    header.innerHTML = `
        <div class="profile-avatar">${currentUser.username[0].toUpperCase()}</div>
        <div class="profile-name">@${esc(currentUser.username)}</div>
        <div class="profile-bio">${esc(currentUser.bio || '')}</div>
        <div class="profile-stats">
            <div><strong>${currentUser.following || 0}</strong><span>Folge ich</span></div>
            <div><strong>${currentUser.followers || 0}</strong><span>Follower</span></div>
            <div><strong>${currentUser.friends || 0}</strong><span>Freunde</span></div>
        </div>
        <div class="profile-actions">
            <button onclick="editBio()">Bio bearbeiten</button>
            <button onclick="logout()">Abmelden</button>
        </div>
    `;

    const tabsContainer = document.getElementById('profile-tabs');
    tabsContainer.innerHTML = `
        <button class="profile-tab ${profileTab === 'videos' ? 'active' : ''}" data-ptab="videos">Videos</button>
        <button class="profile-tab ${profileTab === 'likes' ? 'active' : ''}" data-ptab="likes">Gefällt mir</button>
        <button class="profile-tab ${profileTab === 'reposts' ? 'active' : ''}" data-ptab="reposts">Reposts</button>
        <button class="profile-tab ${profileTab === 'favorites' ? 'active' : ''}" data-ptab="favorites">Favoriten</button>
    `;

    document.querySelectorAll('.profile-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            profileTab = tab.dataset.ptab;
            document.querySelectorAll('.profile-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            loadProfileTab();
        });
    });

    loadProfileTab();
}

async function loadProfileTab() {
    if (!currentUser) return;
    const container = document.getElementById('profile-videos');
    container.innerHTML = '<div class="empty-state">Lade...</div>';
    try {
        let videos = [];
        if (profileTab === 'videos') {
            const data = await API.getVideos(currentUser.username);
            videos = data.videos || [];
        } else if (profileTab === 'likes') {
            const likedIds = currentUser.likes || [];
            if (likedIds.length > 0) {
                const data = await API.getVideosByIds(likedIds);
                videos = data.videos || [];
            }
        } else if (profileTab === 'reposts') {
            const repostIds = currentUser.reposts || [];
            if (repostIds.length > 0) {
                const data = await API.getVideosByIds(repostIds);
                videos = data.videos || [];
            }
        } else if (profileTab === 'favorites') {
            const favIds = currentUser.favorites || [];
            if (favIds.length > 0) {
                const data = await API.getVideosByIds(favIds);
                videos = data.videos || [];
            }
        }
        renderVideoGrid(container, videos);
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">Fehler: ${ex.message}</div>`;
    }
}

async function showUserProfile(username) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById('view-user').classList.add('active');

    const user = await API.getUser(username);
    const header = document.getElementById('user-profile-header');

    let actions = '';
    if (currentUser && currentUser.username !== username) {
        if (user.isFriend) {
            actions = '<button disabled>' + checkIcon() + 'Freund</button>';
        } else if (user.friendRequestSent) {
            actions = '<button disabled>Anfrage gesendet</button>';
        } else {
            actions = `<button class="primary" onclick="sendFriendReq('${username}')">Freund hinzufuegen</button>`;
        }
        actions += user.isFollowing
            ? `<button onclick="doUnfollow('${username}')">Entfolgen</button>`
            : `<button class="primary" onclick="doFollow('${username}')">Folgen</button>`;
    }

    header.innerHTML = `
        <div class="profile-avatar">${username[0].toUpperCase()}</div>
        <div class="profile-name">@${esc(username)}</div>
        <div class="profile-bio">${esc(user.bio || user.visibleName || '')}</div>
        <div class="profile-stats">
            <div><strong>${user.following}</strong><span>Folge ich</span></div>
            <div><strong>${user.followers}</strong><span>Follower</span></div>
            <div><strong>${user.friends}</strong><span>Freunde</span></div>
        </div>
        <div class="profile-actions">${actions}</div>
        <div class="profile-tabs" style="margin-top:0.5rem;">
            <button class="profile-tab active" data-uprof="videos">
                <svg class="icon icon-sm" viewBox="0 0 24 24"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/><line x1="2" y1="7" x2="7" y2="7"/><line x1="2" y1="17" x2="7" y2="17"/><line x1="17" y1="7" x2="22" y2="7"/><line x1="17" y1="17" x2="22" y2="17"/></svg>
            </button>
            <button class="profile-tab" data-uprof="reposts">
                <svg class="icon icon-sm" viewBox="0 0 24 24"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
            </button>
        </div>
    `;

    const container = document.getElementById('user-profile-videos');
    container.innerHTML = '<div class="empty-state">Lade...</div>';

    const data = await API.getVideos(username);
    renderVideoGrid(container, data.videos || []);

    const userReposts = user.reposts || [];
    document.querySelectorAll('[data-uprof]').forEach(tab => {
        tab.addEventListener('click', async () => {
            document.querySelectorAll('[data-uprof]').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            container.innerHTML = '<div class="empty-state">Lade...</div>';
            if (tab.dataset.uprof === 'videos') {
                const d = await API.getVideos(username);
                renderVideoGrid(container, d.videos || []);
            } else if (tab.dataset.uprof === 'reposts') {
                if (userReposts.length > 0) {
                    const d = await API.getVideosByIds(userReposts);
                    renderVideoGrid(container, d.videos || []);
                } else {
                    container.innerHTML = '<div class="empty-state">Keine Reposts</div>';
                }
            }
        });
    });
}

/* ── Inbox ── */
async function loadInbox() {
    if (!currentUser) return;
    currentUser = await API.me();

    const reqContainer = document.getElementById('friend-requests');
    const friendsContainer = document.getElementById('friends-list');
    const chatListContainer = document.getElementById('friends-chat-list');

    // Friend requests
    const requests = currentUser.friendRequestsReceived || [];
    if (requests.length === 0) {
        reqContainer.innerHTML = '';
    } else {
        reqContainer.innerHTML = '<h3 style="font-size:0.8rem;color:var(--text-muted);padding:0.75rem 1rem 0.25rem;text-transform:uppercase;letter-spacing:0.5px;">Anfragen</h3>' +
            requests.map(u => `
                <div class="friend-item">
                    <span>@${esc(u)}</span>
                    <button onclick="acceptFriend('${u}')">Annehmen</button>
                </div>
            `).join('');
    }

    // Friends list
    const friends = currentUser.friendsList || [];
    if (friends.length === 0) {
        friendsContainer.innerHTML = '<div class="empty-state">Noch keine Freunde</div>';
        chatListContainer.innerHTML = '';
    } else {
        friendsContainer.innerHTML = friends.map(u => `
            <div class="friend-item">
                <span onclick="showUserProfile('${u}')" style="cursor:pointer">@${esc(u)}</span>
                <button onclick="openChat('${u}')">Chat</button>
            </div>
        `).join('');
        chatListContainer.innerHTML = friends.map(u => `
            <div class="friend-item" style="cursor:pointer" onclick="openChat('${u}')">
                <span>@${esc(u)}</span>
                <span style="color:var(--text-muted);font-size:0.8rem;">Chat</span>
            </div>
        `).join('');
    }

    // Activity feed
    loadInboxActivity();
}

async function loadInboxActivity() {
    const container = document.getElementById('inbox-activity');
    container.innerHTML = '<div class="empty-state">Lade Aktivitaeten...</div>';
    try {
        const data = await API.getInbox();
        const activities = data.activities || [];
        if (activities.length === 0) {
            container.innerHTML = '<div class="empty-state">Keine aktuellen Aktivitaten von Freunden</div>';
            return;
        }
        container.innerHTML = activities.map(a => `
            <div class="inbox-item" data-video="${a.videoId}">
                <div class="avatar-sm">${a.username[0].toUpperCase()}</div>
                <div class="inbox-text">
                    <strong>@${esc(a.username)}</strong>
                    <span>hat ein Video gefallt: ${esc(a.videoTitle)}</span>
                </div>
                <div class="inbox-time">${timeAgo(a.createdAt)}</div>
            </div>
        `).join('');
        container.querySelectorAll('.inbox-item').forEach(el => {
            el.addEventListener('click', () => openVideoPreview(el.dataset.video));
        });
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">${ex.message}</div>`;
    }
}

/* ── Chat ── */
let chatPartner = null;
let chatPollInterval = null;

function openChat(username) {
    chatPartner = username;
    document.getElementById('chat-partner-name').textContent = '@' + username;
    document.getElementById('chat-modal').classList.remove('hidden');
    document.getElementById('chat-messages').innerHTML = '<div class="empty-state">Lade...</div>';
    loadChatMessages(username);
    if (chatPollInterval) clearInterval(chatPollInterval);
    chatPollInterval = setInterval(() => loadChatMessages(username, true), 10000);
}

async function loadChatMessages(username, silent) {
    try {
        const data = await API.getMessages(username);
        const msgs = data.messages || [];
        const container = document.getElementById('chat-messages');
        if (silent && container.querySelector('.empty-state')) silent = false;
        if (!silent) {
            if (msgs.length === 0) {
                container.innerHTML = '<div class="empty-state">Keine Nachrichten. Schreib etwas!</div>';
                return;
            }
            container.innerHTML = msgs.map(m => `
                <div class="chat-msg ${m.from === currentUser.username ? 'sent' : 'received'}">
                    ${esc(m.text)}
                    ${m.videoId ? `<button class="chat-video-btn" onclick="openVideoPreview('${m.videoId}')">${playIcon()} Video ansehen</button>` : ''}
                    <div class="time">${timeAgo(m.createdAt)}</div>
                </div>
            `).join('');
            container.scrollTop = container.scrollHeight;
        } else {
            if (msgs.length > container.children.length) {
                container.innerHTML = msgs.map(m => `
                    <div class="chat-msg ${m.from === currentUser.username ? 'sent' : 'received'}">
                        ${esc(m.text)}
                        ${m.videoId ? `<button class="chat-video-btn" onclick="openVideoPreview('${m.videoId}')">${playIcon()} Video ansehen</button>` : ''}
                        <div class="time">${timeAgo(m.createdAt)}</div>
                    </div>
                `).join('');
                container.scrollTop = container.scrollHeight;
            }
        }
    } catch (ex) {
        if (!silent) document.getElementById('chat-messages').innerHTML = `<div class="empty-state">${ex.message}</div>`;
    }
}

async function sendChatMessage() {
    const text = document.getElementById('chat-text').value.trim();
    if (!text || !chatPartner) return;
    document.getElementById('chat-text').value = '';
    try {
        await API.sendMessage(chatPartner, text);
        loadChatMessages(chatPartner);
    } catch (ex) {
        alert(ex.message);
    }
}

async function acceptFriend(username) {
    await API.friendAccept(username);
    loadInbox();
}

async function sendFriendReq(username) {
    await API.friendRequest(username);
    showUserProfile(username);
}

async function doFollow(username) {
    await API.follow(username);
    showUserProfile(username);
}

async function doUnfollow(username) {
    await API.unfollow(username);
    showUserProfile(username);
}

function editBio() {
    const bio = prompt('Neue Bio:', currentUser.bio || '');
    if (bio !== null) {
        API.updateProfile({ bio }).then(() => loadProfile());
    }
}

async function logout() {
    await API.logout().catch(() => {});
    API.setToken(null);
    currentUser = null;
    stopPolling();
    showAuth();
}

function renderVideoGrid(container, videos) {
    if (!videos.length) {
        container.innerHTML = '<div class="empty-state">Keine Videos</div>';
        return;
    }
    container.innerHTML = videos.map(v => `
        <div class="profile-video-thumb" data-video="${v.id}">
            <video src="${API.mediaUrl(v.mediaPath)}" preload="metadata" muted playsinline></video>
            <div class="thumb-play-btn">${playIcon()}</div>
            <div class="thumb-title">${esc(v.title)}</div>
        </div>
    `).join('');
    container.querySelectorAll('.profile-video-thumb').forEach(el => {
        el.addEventListener('click', () => openVideoPreview(el.dataset.video));
    });
}

function openVideoPreview(videoId) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById('view-feed').classList.add('active');
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    document.querySelector('[data-view="feed"]').classList.add('active');

    feedOffset = 0;
    feedHasMore = false;

    const video = feedVideos.find(v => v.id === videoId);
    if (video) {
        const container = document.getElementById('feed-container');
        container.innerHTML = '';
        container.appendChild(createFeedItem(video, 0));
        feedVideos = [video];
        setupFeedObserver();
        return;
    }

    API.getVideo(videoId).then(v => {
        const container = document.getElementById('feed-container');
        container.innerHTML = '';
        container.appendChild(createFeedItem(v, 0));
        feedVideos = [v];
        setupFeedObserver();
    }).catch(() => showToast('Video nicht gefunden'));
}

function fileToBase64(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result.split(',')[1]);
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}

function formatCount(n) {
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return String(n);
}

function timeAgo(ts) {
    const diff = Date.now() - ts;
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}m`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h`;
    return `${Math.floor(hours / 24)}d`;
}

function esc(s) {
    const d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}

function showToast(msg) {
    let toast = document.getElementById('toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast';
        toast.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:var(--surface2);color:var(--text);padding:10px 20px;border-radius:8px;font-size:0.85rem;z-index:999;box-shadow:0 4px 12px rgba(0,0,0,0.5);transition:opacity 0.3s;opacity:0;';
        document.body.appendChild(toast);
    }
    toast.textContent = msg;
    toast.style.opacity = '1';
    clearTimeout(toast._hide);
    toast._hide = setTimeout(() => { toast.style.opacity = '0'; }, 2000);
}

window.editBio = editBio;
window.logout = logout;
window.acceptFriend = acceptFriend;
window.sendFriendReq = sendFriendReq;
window.doFollow = doFollow;
window.doUnfollow = doUnfollow;
window.showUserProfile = showUserProfile;
window.openChat = openChat;
window.openVideoPreview = openVideoPreview;
