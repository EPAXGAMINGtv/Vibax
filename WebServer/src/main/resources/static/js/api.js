const API = {
    token: localStorage.getItem('vibax_token') || null,

    headers() {
        const h = { 'Content-Type': 'application/json' };
        if (this.token) h['Authorization'] = `Bearer ${this.token}`;
        return h;
    },

    async request(method, path, body) {
        const opts = { method, headers: this.headers() };
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(path, opts);
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
        return data;
    },

    setToken(token) {
        this.token = token;
        if (token) localStorage.setItem('vibax_token', token);
        else localStorage.removeItem('vibax_token');
    },

    login(username, password) {
        return this.request('POST', '/api/auth/login', { username, password });
    },

    register(username, visibleName, password) {
        return this.request('POST', '/api/auth/register', { username, visibleName, password });
    },

    logout() {
        return this.request('POST', '/api/auth/logout', {});
    },

    me() { return this.request('GET', '/api/auth/me'); },

    getFeed(limit = 20) { return this.request('GET', `/api/feed?limit=${limit}`); },

    getUser(username) { return this.request('GET', `/api/users/${username}`); },

    searchUsers(q) { return this.request('GET', `/api/users/search?q=${encodeURIComponent(q)}`); },

    searchVideos(q) { return this.request('GET', `/api/videos/search?q=${encodeURIComponent(q)}`); },

    getVideos(user) {
        const url = user ? `/api/videos?user=${encodeURIComponent(user)}` : '/api/videos';
        return this.request('GET', url);
    },

    getVideo(id) { return this.request('GET', `/api/videos/${id}`); },

    createVideo(data) { return this.request('POST', '/api/videos', data); },

    like(videoId) { return this.request('POST', '/api/like', { videoId }); },

    unlike(videoId) { return this.request('POST', '/api/unlike', { videoId }); },

    share(videoId) { return this.request('POST', '/api/share', { videoId }); },

    watch(videoId) { return this.request('POST', '/api/watch', { videoId }); },

    follow(username) { return this.request('POST', '/api/follow', { username }); },

    unfollow(username) { return this.request('POST', '/api/unfollow', { username }); },

    friendRequest(username) { return this.request('POST', '/api/friends/request', { username }); },

    friendAccept(username) { return this.request('POST', '/api/friends/accept', { username }); },

    updateProfile(data) { return this.request('PUT', '/api/profile', data); },

    getComments(videoId) { return this.request('GET', `/api/comments/${videoId}`); },

    addComment(videoId, text) { return this.request('POST', '/api/comments', { videoId, text }); },

    uploadMedia(base64, filename) {
        return this.request('POST', '/api/media', { data: base64, filename });
    },

    mediaUrl(path) {
        if (!path) return '';
        return `/api/media/${encodeURIComponent(path.replace(/^\//, ''))}`;
    }
};
