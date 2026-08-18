// ═══════════════════════════════════════════════════════════
//   MOBILE API — Aplikasi Android Hallo Johor
//
// Endpoint publik ini sengaja tidak memakai service-role key di Android.
// Semua akses database tetap terjadi di server Node melalui store.js.
// ═══════════════════════════════════════════════════════════

import {
  getKegiatan,
  getUmkm,
  recordFeatureUsage,
  getLaporanByJid,
  getNextLaporanId,
  saveLaporan,
  uploadLaporanFoto,
  queueMobileReport,
  getLivechatByJid,
  getLatestLivechatByJid,
  startLivechatSession,
  addLivechatMessage,
  closeLivechatSession,
  markLivechatRead,
  saveIvaResult,
} from './store.js';
import {
  MENU_PERSYARATAN,
  PERSYARATAN,
  MENU_PBB,
  MENU_KONTAK,
  MENU_PROGRAM,
  MENU_PARIWISATA,
  MENU_PINTAR_JOHOR,
  WISATA,
  KATEGORI_PENGADUAN,
  KELURAHAN_LIST,
} from './menu.js';
import { IVA_PERTANYAAN } from './iva-flow.js';

const MOBILE_PREFIX = 'mobile:';
const ALLOWED_FEATURES = new Set([
  'home', 'persyaratan', 'pengaduan', 'kegiatan', 'pbb', 'kontak',
  'pintar_johor', 'program', 'wisata', 'umkm', 'iva', 'livechat', 'status',
]);
const ALLOWED_ACTIONS = new Set(['view', 'open', 'submit', 'start', 'message', 'complete']);

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-App-Version',
  'Cache-Control': 'no-store',
};

const ok = (send, payload, code = 200) =>
  send(code, JSON.stringify({ ok: code < 400, ...payload }), 'application/json; charset=utf-8', corsHeaders);

const fail = (send, code, error) => ok(send, { error }, code);

const text = (value, max = 500) => String(value ?? '').trim().slice(0, max);

const clientIdFrom = (value) => {
  const id = text(value, 128);
  // UUID yang dibuat aplikasi Android, tanpa karakter yang dapat dipakai untuk injeksi JID.
  return /^[A-Za-z0-9_-]{8,128}$/.test(id) ? id : null;
};

const clientJid = (id) => `${MOBILE_PREFIX}${id}`;

const finiteNumber = (value, min, max) => {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max ? number : null;
};

const publicLaporan = (item) => ({
  id: item.id,
  namaPelapor: item.namaPelapor || '',
  kontak: item.kontak || '',
  kategori: item.kategori || '',
  kelurahan: item.kelurahan || '',
  isi: item.isi || '',
  fotoUrl: item.fotoUrl || null,
  alamat: item.alamat || '',
  koordinat: item.koordinat || null,
  status: item.status || 'terkirim',
  tanggal: item.tanggal || null,
  statusUpdatedAt: item.statusUpdatedAt || null,
});

const publicSession = (session) => session ? ({
  id: session.id,
  name: session.name || '',
  status: session.status,
  startedAt: session.startedAt,
  lastMessageAt: session.lastMessageAt,
  closedAt: session.closedAt || null,
  messages: session.messages || [],
}) : null;

const bootstrap = async () => {
  const [kegiatan, umkm] = await Promise.all([getKegiatan(), getUmkm()]);
  const requirements = Object.entries(PERSYARATAN).map(([code, content]) => ({
    code,
    title: text(content.split('\n').find(line => line.includes('*')) || `Layanan ${code}`, 120).replaceAll('*', ''),
    content,
  }));
  const wisata = Object.entries(WISATA).map(([code, content]) => ({
    code,
    title: text(content.split('\n').find(line => line.includes('*')) || `Wisata ${code}`, 120).replaceAll('*', ''),
    content,
  }));

  return {
    version: 1,
    contact: {
      office: 'Kantor Kecamatan Medan Johor',
      address: 'Jl. Karya Cipta No. 16, Medan Johor',
      phone: '0813-6777-2047',
      email: 'kec.medanjohor@pemkomedan.go.id',
      mapsUrl: 'https://maps.google.com/?q=Kantor+Kecamatan+Medan+Johor',
      serviceHours: 'Senin–Kamis 08.00–15.00 WIB; Jumat 08.00–11.30 WIB',
    },
    services: [
      { id: 'persyaratan', title: 'Persyaratan Surat', icon: '📋', content: MENU_PERSYARATAN },
      { id: 'pbb', title: 'Pajak PBB', icon: '💰', content: MENU_PBB },
      { id: 'kontak', title: 'Kontak & Jam Pelayanan', icon: '📞', content: MENU_KONTAK },
      { id: 'pintar_johor', title: 'Pintar Johor', icon: '📚', content: MENU_PINTAR_JOHOR },
      { id: 'program', title: 'Program Kecamatan', icon: '🌟', content: MENU_PROGRAM },
      { id: 'wisata', title: 'Wisata Medan Johor', icon: '🗺️', content: MENU_PARIWISATA },
    ],
    requirements,
    wisata,
    categories: KATEGORI_PENGADUAN,
    kelurahan: KELURAHAN_LIST,
    kegiatan,
    umkm,
    ivaQuestions: IVA_PERTANYAAN.map(question => ({
      id: question.id,
      text: question.teks,
      options: Object.entries(question.pilihan).map(([value, option]) => ({
        value,
        label: option.label,
      })),
    })),
  };
};

const calculateIva = (answers) => {
  if (!answers || typeof answers !== 'object') return null;
  const answerMap = Array.isArray(answers)
    ? Object.fromEntries(answers.map(item => [item?.questionId, item?.value]))
    : answers;
  let score = 0;
  const jawaban = {};

  for (const question of IVA_PERTANYAAN) {
    const value = text(answerMap[question.id], 8);
    const option = question.pilihan[value];
    if (!option) return null;
    score += option.skor;
    jawaban[option.field] = option.label;
  }

  const risiko = score <= 3 ? 'rendah' : score <= 6 ? 'sedang' : 'tinggi';
  return { score, risiko, jawaban };
};

/**
 * Menangani seluruh /api/mobile/*.
 * Return true jika path sudah ditangani, false agar web.js bisa melanjutkan routing.
 */
export const handleMobileApi = async ({ req, path, requestUrl, send, parseJSONBody }) => {
  if (!path.startsWith('/api/mobile/')) return false;

  if (req.method === 'OPTIONS') {
    return send(204, '', 'text/plain; charset=utf-8', corsHeaders), true;
  }

  if (path === '/api/mobile/health' && req.method === 'GET') {
    ok(send, { service: 'Hallo Johor Mobile API', version: 1 });
    return true;
  }

  if (path === '/api/mobile/bootstrap' && req.method === 'GET') {
    try {
      ok(send, await bootstrap());
    } catch (error) {
      fail(send, 500, 'Data layanan belum dapat dimuat.');
    }
    return true;
  }

  if (path === '/api/mobile/usage' && req.method === 'POST') {
    const body = await parseJSONBody(req);
    const clientId = clientIdFrom(body.clientId);
    const feature = text(body.feature, 64);
    const action = text(body.action || 'view', 32);
    if (!clientId || !ALLOWED_FEATURES.has(feature) || !ALLOWED_ACTIONS.has(action)) {
      fail(send, 400, 'Data aktivitas aplikasi tidak valid.');
      return true;
    }
    const saved = await recordFeatureUsage({
      clientId,
      platform: 'android',
      feature,
      action,
      metadata: body.metadata && typeof body.metadata === 'object' ? body.metadata : {},
    });
    if (!saved) fail(send, 500, 'Aktivitas belum dapat dicatat.');
    else ok(send, {});
    return true;
  }

  if (path === '/api/mobile/reports' && req.method === 'GET') {
    const clientId = clientIdFrom(new URL(requestUrl, 'http://localhost').searchParams.get('clientId'));
    if (!clientId) {
      fail(send, 400, 'clientId diperlukan.');
      return true;
    }
    const reports = await getLaporanByJid(clientJid(clientId));
    ok(send, { reports: reports.map(publicLaporan) });
    return true;
  }

  if (path === '/api/mobile/reports' && req.method === 'POST') {
    const body = await parseJSONBody(req);
    const clientId = clientIdFrom(body.clientId);
    const categoryId = text(body.categoryId, 8);
    const wardId = text(body.wardId, 8);
    const category = KATEGORI_PENGADUAN.find(item => item.id === categoryId);
    const ward = KELURAHAN_LIST.find(item => item.id === wardId);
    const name = text(body.name, 100);
    const contact = text(body.contact, 32);
    const description = text(body.description, 2000);
    const latitude = finiteNumber(body.latitude, -90, 90);
    const longitude = finiteNumber(body.longitude, -180, 180);
    const photoBase64 = text(body.photoBase64, 7_500_000);
    const photoMime = text(body.photoMime || 'image/jpeg', 40);

    if (!clientId || !category || !ward || name.length < 2 || description.length < 5 || latitude === null || longitude === null) {
      fail(send, 400, 'Nama, kategori, kelurahan, uraian, dan lokasi wajib diisi.');
      return true;
    }
    if (contact && !/^[+0-9][0-9\s().-]{7,24}$/.test(contact)) {
      fail(send, 400, 'Nomor kontak tidak valid.');
      return true;
    }
    if (photoBase64 && (!photoMime.startsWith('image/') || photoBase64.length > 7_000_000)) {
      fail(send, 400, 'Foto tidak valid atau terlalu besar.');
      return true;
    }

    const laporanId = await getNextLaporanId();
    let fotoUrl = null;
    if (photoBase64) {
      fotoUrl = await uploadLaporanFoto(laporanId, Buffer.from(photoBase64, 'base64'), photoMime);
    }
    const now = new Date().toISOString();
    const alamat = text(body.address, 300) || `Koordinat: ${latitude}, ${longitude}`;
    const report = {
      id: laporanId,
      pelapor: clientJid(clientId),
      namaPelapor: name,
      kontak: contact,
      kategori: category.label,
      kelurahan: ward.label,
      isi: description,
      koordinat: { lat: latitude, lon: longitude },
      alamat,
      fotoUrl,
      tanggal: now,
      status: 'terkirim',
    };
    const saved = await saveLaporan(report);
    if (!saved) {
      fail(send, 500, 'Laporan belum dapat disimpan.');
      return true;
    }

    // Bot akan meneruskan ke grup WhatsApp bila proses bot sedang aktif.
    // Dashboard sudah bisa melihat arsip meskipun worker bot sedang offline.
    await queueMobileReport({
      reportId: laporanId,
      name,
      contact,
      category: report.kategori,
      categoryEmoji: category.emoji,
      categoryLabel: category.label,
      ward: report.kelurahan,
      description,
      latitude,
      longitude,
      address: alamat,
      photoUrl: fotoUrl,
    });
    await recordFeatureUsage({ clientId, platform: 'android', feature: 'pengaduan', action: 'submit', metadata: { reportId: String(laporanId) } });

    ok(send, { report: publicLaporan(report) }, 201);
    return true;
  }

  if (path === '/api/mobile/iva' && req.method === 'POST') {
    const body = await parseJSONBody(req);
    const clientId = clientIdFrom(body.clientId);
    const result = calculateIva(body.answers);
    const name = text(body.name || 'Pengguna Aplikasi', 100);
    if (!clientId || !result) {
      fail(send, 400, 'Jawaban skrining belum lengkap.');
      return true;
    }
    const saved = await saveIvaResult({
      waNumber: clientJid(clientId),
      nama: name,
      skor: result.score,
      risiko: result.risiko,
      jawaban: result.jawaban,
    });
    if (!saved) fail(send, 500, 'Hasil skrining belum dapat disimpan.');
    else {
      await recordFeatureUsage({ clientId, platform: 'android', feature: 'iva', action: 'complete', metadata: { risiko: result.risiko } });
      ok(send, { score: result.score, risk: result.risiko, answers: result.jawaban });
    }
    return true;
  }

  if (path === '/api/mobile/livechat' && req.method === 'GET') {
    const clientId = clientIdFrom(new URL(requestUrl, 'http://localhost').searchParams.get('clientId'));
    if (!clientId) {
      fail(send, 400, 'clientId diperlukan.');
      return true;
    }
    const session = await getLatestLivechatByJid(clientJid(clientId));
    if (session?.status === 'active') await markLivechatRead(session.id);
    ok(send, { session: publicSession(session) });
    return true;
  }

  if (path === '/api/mobile/livechat/start' && req.method === 'POST') {
    const body = await parseJSONBody(req);
    const clientId = clientIdFrom(body.clientId);
    const name = text(body.name, 100);
    if (!clientId || name.length < 2) {
      fail(send, 400, 'Nama dan clientId diperlukan.');
      return true;
    }
    let session = await getLivechatByJid(clientJid(clientId));
    if (!session) session = await startLivechatSession(clientJid(clientId), name);
    if (!session) fail(send, 500, 'Sesi LiveChat belum dapat dibuat.');
    else {
      await recordFeatureUsage({ clientId, platform: 'android', feature: 'livechat', action: 'start' });
      ok(send, { session: publicSession(session) }, 201);
    }
    return true;
  }

  if (path === '/api/mobile/livechat/message' && req.method === 'POST') {
    const body = await parseJSONBody(req);
    const clientId = clientIdFrom(body.clientId);
    const message = text(body.message, 2000);
    if (!clientId || !message) {
      fail(send, 400, 'Pesan tidak boleh kosong.');
      return true;
    }
    const session = await getLivechatByJid(clientJid(clientId));
    if (!session) {
      fail(send, 404, 'Sesi LiveChat tidak ditemukan.');
      return true;
    }
    const result = await addLivechatMessage(clientJid(clientId), 'user', message);
    if (!result) fail(send, 400, 'Sesi LiveChat sudah ditutup.');
    else {
      await recordFeatureUsage({ clientId, platform: 'android', feature: 'livechat', action: 'message' });
      ok(send, { session: publicSession(result.session) });
    }
    return true;
  }

  if (path === '/api/mobile/livechat/close' && req.method === 'POST') {
    const body = await parseJSONBody(req);
    const clientId = clientIdFrom(body.clientId);
    if (!clientId) {
      fail(send, 400, 'clientId diperlukan.');
      return true;
    }
    await closeLivechatSession(clientJid(clientId));
    ok(send, {});
    return true;
  }

  // Path /api/mobile/* selalu dijawab agar typo tidak jatuh ke redirect login HTML.
  fail(send, 404, 'Endpoint aplikasi tidak ditemukan.');
  return true;
};

export { corsHeaders };
