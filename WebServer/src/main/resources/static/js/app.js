let currentUser = null;
let feedVideos = [];
let currentVideoId = null;
let viewedVideos = new Set();

document.addEventListener('DOMContentLoaded', init);

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
            currentUser = data.user || await API.me();
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
}

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
            if (view === 'friends') loadFriends();
        });
    });

    document.getElementById('back-from-user').addEventListener('click', () => {
        document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
        document.getElementById('view-feed').classList.add('active');
        document.querySelector('[data-view="feed"]').classList.add('active');
    });
}

async function loadFeed() {
    const container = document.getElementById('feed-container');
    container.innerHTML = '<div class="empty-state">Lade Feed...</div>';

    try {
        const data = await API.getFeed(30);
        feedVideos = data.videos || [];

        if (feedVideos.length === 0) {
            container.innerHTML = '<div class="empty-state">Noch keine Videos. Sei der Erste!</div>';
            return;
        }

        container.innerHTML = '';
        feedVideos.forEach((video, i) => {
            container.appendChild(createFeedItem(video, i));
        });

        setupFeedObserver();
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">Fehler: ${ex.message}</div>`;
    }
}

function createFeedItem(video, index) {
    const item = document.createElement('div');
    item.className = 'feed-item';
    item.dataset.videoId = video.id;
    item.dataset.index = index;

    const isImage = video.mediaType === 'image';
    const mediaEl = isImage
        ? `<img class="feed-media" src="${API.mediaUrl(video.mediaPath)}" alt="${video.title}">`
        : `<video class="feed-media" src="${API.mediaUrl(video.mediaPath)}" loop muted playsinline preload="auto"></video>`;

    const liked = currentUser && currentUser.likes && currentUser.likes.includes(video.id);

    item.innerHTML = `
        ${mediaEl}
        <div class="feed-overlay">
            <div class="feed-author" data-user="${video.authorUsername}">@${video.authorUsername}</div>
            <div class="feed-title">${esc(video.title)}</div>
            <div class="feed-desc">${esc(video.description || '')}</div>
            <div class="feed-tags">${(video.tags || []).map(t => '#' + t).join(' ')}</div>
        </div>
        <div class="feed-actions">
            <div class="feed-avatar" data-user="${video.authorUsername}">${video.authorUsername[0].toUpperCase()}</div>
            <button class="action-btn like-btn ${liked ? 'liked' : ''}" data-id="${video.id}">
                <span class="icon">❤</span>
                <span class="count">${formatCount(video.likes)}</span>
            </button>
            <button class="action-btn comment-btn" data-id="${video.id}">
                <span class="icon">💬</span>
                <span class="count">${formatCount(video.comments)}</span>
            </button>
            <button class="action-btn share-btn" data-id="${video.id}">
                <span class="icon">↗</span>
                <span class="count">${formatCount(video.shares)}</span>
            </button>
            ${isImage ? '' : '<button class="action-btn sound-btn" id="sound-' + video.id + '"><span class="icon">🔇</span></button>'}
        </div>
    `;

    item.querySelector('.like-btn').addEventListener('click', () => toggleLike(video.id, item));
    item.querySelector('.comment-btn').addEventListener('click', () => openComments(video.id));
    item.querySelector('.share-btn').addEventListener('click', () => openShare(video.id));
    item.querySelectorAll('[data-user]').forEach(el => {
        el.addEventListener('click', () => showUserProfile(el.dataset.user));
    });

    const mediaElClick = item.querySelector('.feed-media');
    if (mediaElClick) mediaElClick.addEventListener('click', () => openVideoPreview(video.id));

    const soundBtn = item.querySelector('.sound-btn');
    if (soundBtn) {
        soundBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const video = item.querySelector('video');
            if (!video) return;
            video.muted = !video.muted;
            soundBtn.querySelector('.icon').textContent = video.muted ? '🔇' : '🔊';
        });
    }

    const videoEl = item.querySelector('video');
    if (videoEl) videoEl.load();

    return item;
}

function setupFeedObserver() {
    const container = document.getElementById('feed-container');
    const observer = new IntersectionObserver(entries => {
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

    container.querySelectorAll('.feed-item').forEach(item => observer.observe(item));
}

async function toggleLike(videoId, item) {
    if (!currentUser) return alert('Bitte anmelden');
    const btn = item.querySelector('.like-btn');
    const countEl = btn.querySelector('.count');
    try {
        if (btn.classList.contains('liked')) {
            await API.unlike(videoId);
            btn.classList.remove('liked');
            countEl.textContent = formatCount(Math.max(0, parseInt(countEl.textContent) - 1));
        } else {
            await API.like(videoId);
            btn.classList.add('liked');
            countEl.textContent = formatCount(parseInt(countEl.textContent) + 1);
        }
    } catch (ex) {
        alert(ex.message);
    }
}

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
        alert('Link kopiert!');
    });
    document.getElementById('chat-send').addEventListener('click', sendChatMessage);
    document.getElementById('chat-text').addEventListener('keypress', e => {
        if (e.key === 'Enter') sendChatMessage();
    });

    document.getElementById('close-video-preview').addEventListener('click', () => {
        document.getElementById('video-preview-modal').classList.add('hidden');
        document.getElementById('video-preview-container').innerHTML = '';
    });
    document.getElementById('video-preview-modal').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) {
            document.getElementById('video-preview-modal').classList.add('hidden');
            document.getElementById('video-preview-container').innerHTML = '';
        }
    });
}

async function openComments(videoId) {
    if (!currentUser) return alert('Bitte anmelden');
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
        alert('Video an @' + friend + ' gesendet!');
    } catch (ex) {
        alert(ex.message);
    }
}

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
            document.getElementById('progress-text').textContent = 'Datei ausgewählt: ' + file.name;
        }
    });

    document.getElementById('upload-btn').addEventListener('click', async () => {
        if (!currentUser) return alert('Bitte anmelden');

        const title = document.getElementById('upload-title').value.trim();
        const desc = document.getElementById('upload-desc').value.trim();
        const tags = document.getElementById('upload-tags').value.trim();
        const file = document.getElementById('upload-file').files[0];

        if (!title) return alert('Titel erforderlich');

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
                uploadBtn.textContent = 'Upload läuft...';

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

async function loadProfile() {
    if (!currentUser) return;
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

    const data = await API.getVideos(currentUser.username);
    renderVideoGrid(document.getElementById('profile-videos'), data.videos || []);
}

async function showUserProfile(username) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById('view-user').classList.add('active');

    const user = await API.getUser(username);
    const header = document.getElementById('user-profile-header');

    let actions = '';
    if (currentUser && currentUser.username !== username) {
        if (user.isFriend) {
            actions = '<button disabled>Freund ✓</button>';
        } else if (user.friendRequestSent) {
            actions = '<button disabled>Anfrage gesendet</button>';
        } else {
            actions = `<button class="primary" onclick="sendFriendReq('${username}')">Freund hinzufügen</button>`;
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
    `;

    const data = await API.getVideos(username);
    renderVideoGrid(document.getElementById('user-profile-videos'), data.videos || []);
}

let chatPartner = null;
let chatPollInterval = null;

async function loadFriends() {
    if (!currentUser) return;
    currentUser = await API.me();

    const reqContainer = document.getElementById('friend-requests');
    const friendsContainer = document.getElementById('friends-list');
    const chatListContainer = document.getElementById('friends-chat-list');

    const requests = currentUser.friendRequestsReceived || [];
    if (requests.length === 0) {
        reqContainer.innerHTML = '<div class="empty-state">Keine Anfragen</div>';
    } else {
        reqContainer.innerHTML = requests.map(u => `
            <div class="friend-item">
                <span>@${esc(u)}</span>
                <button onclick="acceptFriend('${u}')">Annehmen</button>
            </div>
        `).join('');
    }

    friendsContainer.innerHTML = '<div class="empty-state">Lade Freunde...</div>';
    const friends = currentUser.friendsList || [];
    if (friends.length === 0) {
        friendsContainer.innerHTML = '<div class="empty-state">Noch keine Freunde</div>';
        chatListContainer.innerHTML = '';
    } else {
        friendsContainer.innerHTML = friends.map(u => `
            <div class="friend-item">
                <span>@${esc(u)}</span>
                <button onclick="showUserProfile('${u}')">Profil</button>
                <button class="friend-chat-btn" onclick="openChat('${u}')">💬</button>
            </div>
        `).join('');
        chatListContainer.innerHTML = '<h2 class="view-title" style="margin-top:1rem">Chats</h2>' +
            friends.map(u => `
                <div class="friend-item" style="cursor:pointer" onclick="openChat('${u}')">
                    <span>@${esc(u)}</span>
                    <span style="color:var(--text-muted);font-size:0.8rem">💬 Chat</span>
                </div>
            `).join('');
    }
}

function openChat(username) {
    chatPartner = username;
    document.getElementById('chat-partner-name').textContent = '@' + username;
    document.getElementById('chat-modal').classList.remove('hidden');
    document.getElementById('chat-messages').innerHTML = '<div class="empty-state">Lade...</div>';
    loadChatMessages(username);
    if (chatPollInterval) clearInterval(chatPollInterval);
    chatPollInterval = setInterval(() => loadChatMessages(username, true), 3000);
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
                    ${m.videoId ? '<br><small style="opacity:0.8">📹 Video geteilt</small>' : ''}
                    <div class="time">${timeAgo(m.createdAt)}</div>
                </div>
            `).join('');
            container.scrollTop = container.scrollHeight;
        } else {
            if (msgs.length > container.children.length) {
                container.innerHTML = msgs.map(m => `
                    <div class="chat-msg ${m.from === currentUser.username ? 'sent' : 'received'}">
                        ${esc(m.text)}
                        ${m.videoId ? '<br><small style="opacity:0.8">📹 Video geteilt</small>' : ''}
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
    loadFriends();
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
            <div class="thumb-play-btn">▶</div>
            <div class="thumb-title">${esc(v.title)}</div>
        </div>
    `).join('');
    container.querySelectorAll('.profile-video-thumb').forEach(el => {
        el.addEventListener('click', () => openVideoPreview(el.dataset.video));
    });
}

function openVideoPreview(videoId) {
    const video = feedVideos.find(v => v.id === videoId);
    if (!video) {
        API.getVideo(videoId).then(v => {
            document.getElementById('video-preview-modal').classList.remove('hidden');
            document.getElementById('video-preview-container').innerHTML = createVideoPreviewHtml(v);
            const pv = document.querySelector('.preview-media');
            if (pv && pv.tagName === 'VIDEO') pv.play().catch(() => {});
        }).catch(() => alert('Video nicht gefunden'));
        return;
    }
    document.getElementById('video-preview-modal').classList.remove('hidden');
    document.getElementById('video-preview-container').innerHTML = createVideoPreviewHtml(video);
    const pv = document.querySelector('.preview-media');
    if (pv && pv.tagName === 'VIDEO') pv.play().catch(() => {});
}

function createVideoPreviewHtml(video) {
    const isImage = video.mediaType === 'image';
    const media = isImage
        ? `<img class="preview-media" src="${API.mediaUrl(video.mediaPath)}" alt="${esc(video.title)}">`
        : `<video class="preview-media" src="${API.mediaUrl(video.mediaPath)}" controls autoplay playsinline preload="auto"></video>`;
    return `
        <div class="preview-header">
            <div class="preview-author" onclick="showUserProfile('${video.authorUsername}')">@${esc(video.authorUsername)}</div>
            <div class="preview-title">${esc(video.title)}</div>
            <div class="preview-desc">${esc(video.description || '')}</div>
            <div class="preview-tags">${(video.tags || []).map(t => '#' + t).join(' ')}</div>
        </div>
        ${media}
        <div class="preview-stats">
            <span>❤ ${formatCount(video.likes)}</span>
            <span>💬 ${formatCount(video.comments)}</span>
            <span>👁 ${formatCount(video.views)}</span>
        </div>
    `;
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

window.editBio = editBio;
window.logout = logout;
window.acceptFriend = acceptFriend;
window.sendFriendReq = sendFriendReq;
window.doFollow = doFollow;
window.doUnfollow = doUnfollow;
window.showUserProfile = showUserProfile;
window.openChat = openChat;
