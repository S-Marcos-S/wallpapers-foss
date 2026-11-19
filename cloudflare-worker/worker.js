/**
 * Cloudflare Worker: High-Performance Secure Media CDN for Wallpapers FOSS
 * 
 * Features:
 * - Edge Caching via Cloudflare Global CDN (immutable, cached for 1 year).
 * - 100% Secure: BOT_TOKEN is kept strictly as an encrypted Cloudflare Secret.
 * - Streams Telegram Bot API media directly to clients with zero token exposure.
 * - Supports Range requests for video/live wallpapers (MP4).
 * - Input validation against path traversal and unauthorized API calls.
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
  "Access-Control-Allow-Headers": "Range, Content-Type, User-Agent",
  "Access-Control-Expose-Headers": "Content-Length, Content-Range, Content-Type, ETag",
};

export default {
  async fetch(request, env, ctx) {
    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // Health check endpoint
    if (path === "/" || path === "/health") {
      return new Response(
        JSON.stringify({
          status: "online",
          service: "Wallpapers FOSS CDN",
          version: "1.0.0",
        }),
        {
          headers: {
            "Content-Type": "application/json",
            ...CORS_HEADERS,
          },
        }
      );
    }

    // Check Cloudflare Edge Cache first
    const cache = caches.default;
    let cachedResponse = await cache.match(request);
    if (cachedResponse) {
      const response = new Response(cachedResponse.body, cachedResponse);
      for (const [key, val] of Object.entries(CORS_HEADERS)) {
        response.headers.set(key, val);
      }
      return response;
    }

    // Route: /file/:file_id (Download from Telegram Bot API via file_id)
    if (path.startsWith("/file/")) {
      const fileId = path.replace("/file/", "").split("?")[0].trim();
      return handleTelegramFile(request, fileId, env, ctx);
    }

    // Route: /raw/:github_path (Fallback / migration proxy for GitHub raw media)
    if (path.startsWith("/raw/")) {
      const rawPath = path.replace("/raw/", "").trim();
      return handleGitHubRaw(request, rawPath, ctx);
    }

    return new Response("Not Found", { status: 404, headers: CORS_HEADERS });
  },
};

/**
 * Resolves a Telegram file_id using getFile and streams the file with Edge Caching.
 */
async function handleTelegramFile(request, fileId, env, ctx) {
  const botToken = env.BOT_TOKEN;
  if (!botToken) {
    return new Response(
      JSON.stringify({ error: "Server configuration error: BOT_TOKEN not set in environment secrets." }),
      { status: 500, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
    );
  }

  // Validate file_id format (alphanumeric, underscores, hyphens)
  if (!fileId || !/^[A-Za-z0-9_-]{10,256}$/.test(fileId)) {
    return new Response(
      JSON.stringify({ error: "Invalid file_id parameter." }),
      { status: 400, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
    );
  }

  try {
    // 1. Resolve file_path from Telegram Bot API getFile
    const getFileUrl = `https://api.telegram.org/bot${botToken}/getFile?file_id=${encodeURIComponent(fileId)}`;
    const metaResp = await fetch(getFileUrl, {
      headers: { "User-Agent": "WallpapersCDN/1.0" },
      cf: { cacheTtl: 86400 }, // Cache file_path metadata for 24 hours
    });

    if (!metaResp.ok) {
      return new Response(
        JSON.stringify({ error: "Failed to resolve file from Telegram.", status: metaResp.status }),
        { status: metaResp.status, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
      );
    }

    const meta = await metaResp.json();
    if (!meta.ok || !meta.result || !meta.result.file_path) {
      return new Response(
        JSON.stringify({ error: "Telegram file not found or expired.", details: meta }),
        { status: 404, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
      );
    }

    const filePath = meta.result.file_path;
    // Security check on filePath returned by Telegram
    if (filePath.includes("..") || filePath.startsWith("/")) {
      return new Response("Invalid file path", { status: 400, headers: CORS_HEADERS });
    }

    // 2. Stream media file from Telegram's file server
    const downloadUrl = `https://api.telegram.org/file/bot${botToken}/${filePath}`;
    const forwardHeaders = new Headers();
    if (request.headers.has("Range")) {
      forwardHeaders.set("Range", request.headers.get("Range"));
    }
    forwardHeaders.set("User-Agent", "WallpapersCDN/1.0");

    const fileResp = await fetch(downloadUrl, { headers: forwardHeaders });
    if (!fileResp.ok && fileResp.status !== 206) {
      return new Response("Error fetching media stream from upstream", { status: fileResp.status, headers: CORS_HEADERS });
    }

    // Determine correct content-type
    const ext = filePath.split(".").pop()?.toLowerCase() || "";
    const mimeTypes = {
      jpg: "image/jpeg",
      jpeg: "image/jpeg",
      png: "image/png",
      webp: "image/webp",
      mp4: "video/mp4",
      webm: "video/webm",
    };
    const contentType = mimeTypes[ext] || fileResp.headers.get("Content-Type") || "application/octet-stream";

    const responseHeaders = new Headers(fileResp.headers);
    responseHeaders.set("Content-Type", contentType);
    responseHeaders.set("Cache-Control", "public, max-age=31536000, s-maxage=31536000, immutable");
    for (const [key, val] of Object.entries(CORS_HEADERS)) {
      responseHeaders.set(key, val);
    }

    const response = new Response(fileResp.body, {
      status: fileResp.status,
      statusText: fileResp.statusText,
      headers: responseHeaders,
    });

    // Store in Cloudflare Edge Cache if response is 200 OK
    if (fileResp.status === 200) {
      ctx.waitUntil(caches.default.put(request, response.clone()));
    }

    return response;
  } catch (err) {
    return new Response(
      JSON.stringify({ error: "Internal server error fetching media.", message: err.message }),
      { status: 500, headers: { "Content-Type": "application/json", ...CORS_HEADERS } }
    );
  }
}

/**
 * Fallback proxy for existing GitHub raw media files with Edge Caching.
 */
async function handleGitHubRaw(request, rawPath, ctx) {
  // Sanitize path
  if (!rawPath || rawPath.includes("..") || rawPath.startsWith("/")) {
    return new Response("Invalid raw path", { status: 400, headers: CORS_HEADERS });
  }

  const githubUrl = `https://raw.githubusercontent.com/S-Marcos-S/viagara-wallpapers/main/wallpapers/${rawPath}`;
  const forwardHeaders = new Headers();
  if (request.headers.has("Range")) {
    forwardHeaders.set("Range", request.headers.get("Range"));
  }
  forwardHeaders.set("User-Agent", "WallpapersCDN/1.0");

  const ghResp = await fetch(githubUrl, { headers: forwardHeaders });
  if (!ghResp.ok && ghResp.status !== 206) {
    return new Response("Media not found on upstream", { status: ghResp.status, headers: CORS_HEADERS });
  }

  const responseHeaders = new Headers(ghResp.headers);
  responseHeaders.set("Cache-Control", "public, max-age=31536000, s-maxage=31536000, immutable");
  for (const [key, val] of Object.entries(CORS_HEADERS)) {
    responseHeaders.set(key, val);
  }

  const response = new Response(ghResp.body, {
    status: ghResp.status,
    statusText: ghResp.statusText,
    headers: responseHeaders,
  });

  if (ghResp.status === 200) {
    ctx.waitUntil(caches.default.put(request, response.clone()));
  }

  return response;
}
