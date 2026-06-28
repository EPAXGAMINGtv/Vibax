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
        : `<video class="feed-media" src="${API.mediaUrl(video.mediaPath)}" loop muted playsinline></video>`;

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
        </div>
    `;

    item.querySelector('.like-btn').addEventListener('click', () => toggleLike(video.id, item));
    item.querySelector('.comment-btn').addEventListener('click', () => openComments(video.id));
    item.querySelector('.share-btn').addEventListener('click', () => openShare(video.id));
    item.querySelectorAll('[data-user]').forEach(el => {
        el.addEventListener('click', () => showUserProfile(el.dataset.user));
    });

    return item;
}

function setupFeedObserver() {
    const container = document.getElementById('feed-container');
    const observer = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const videoId = entry.target.dataset.videoId;
                const video = entry.target.querySelector('video');
                if (video) { video.play().catch(() => {}); }
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
    document.getElementById('comment-submit').addEventListener('click', submitComment);
    document.getElementById('comment-text').addEventListener('keypress', e => {
        if (e.key === 'Enter') submitComment();
    });
    document.getElementById('copy-link').addEventListener('click', () => {
        navigator.clipboard.writeText(document.getElementById('share-link').textContent);
        alert('Link kopiert!');
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

async function openShare(videoId) {
    try {
        await API.share(videoId);
        const link = `${window.location.origin}/?video=${videoId}`;
        document.getElementById('share-link').textContent = link;
        document.getElementById('share-modal').classList.remove('hidden');
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
}

async function doSearch(query) {
    const container = document.getElementById('search-results');
    if (!query.trim()) { container.innerHTML = ''; return; }

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
                    <div class="avatar-sm">▶</div>
                    <div><strong>${esc(v.title)}</strong><br><small>@${esc(v.authorUsername)}</small></div>
                </div>
            `).join('');
            html += '</div>';
        }
        container.innerHTML = html || '<div class="empty-state">Keine Ergebnisse</div>';

        container.querySelectorAll('[data-user]').forEach(el => {
            el.addEventListener('click', () => showUserProfile(el.dataset.user));
        });
    } catch (ex) {
        container.innerHTML = `<div class="empty-state">${ex.message}</div>`;
    }
}

function setupUpload() {
    document.getElementById('upload-btn').addEventListener('click', async () => {
        if (!currentUser) return alert('Bitte anmelden');

        const title = document.getElementById('upload-title').value.trim();
        const desc = document.getElementById('upload-desc').value.trim();
        const tags = document.getElementById('upload-tags').value.trim();
        const file = document.getElementById('upload-file').files[0];

        if (!title) return alert('Titel erforderlich');

        try {
            let mediaPath = '';
            let mediaType = 'video';
            if (file) {
                const base64 = await fileToBase64(file);
                const upload = await API.uploadMedia(base64, file.name);
                mediaPath = upload.path;
                mediaType = file.type.startsWith('image/') ? 'image' : 'video';
            }

            await API.createVideo({ title, description: desc, tags, mediaPath, mediaType });
            alert('Video gepostet!');
            document.getElementById('upload-title').value = '';
            document.getElementById('upload-desc').value = '';
            document.getElementById('upload-tags').value = '';
            document.getElementById('upload-file').value = '';
            document.querySelector('[data-view="feed"]').click();
        } catch (ex) {
            alert(ex.message);
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

async function loadFriends() {
    if (!currentUser) return;
    currentUser = await API.me();

    const reqContainer = document.getElementById('friend-requests');
    const friendsContainer = document.getElementById('friends-list');

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
    } else {
        friendsContainer.innerHTML = friends.map(u => `
            <div class="friend-item">
                <span>@${esc(u)}</span>
                <button onclick="showUserProfile('${u}')">Profil</button>
            </div>
        `).join('');
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
            <img src="${API.mediaUrl(v.mediaPath)}" alt="${esc(v.title)}">
        </div>
    `).join('');
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
