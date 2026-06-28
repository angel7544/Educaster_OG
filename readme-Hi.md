# EduCaster System Architecture aur Working (Hinglish)

Namaste! Yeh document EduCaster application ke system design aur uske kaam karne ke tarike ko asaan Hinglish mein samjhata hai.

## 1. EduCaster Kya Hai?
**EduCaster** ek professional desktop application hai jo educational content creators aur teachers ke liye banayi gayi hai. Iska main kaam video content ko manage karna, encode (transcode) karna aur distribute karna hai. 
Yeh VOD (Video-on-Demand) aur Live Streaming dono ko support karta hai. Isme Java (UI aur logic ke liye) aur Python (video processing ke liye) ka powerful combination use kiya gaya hai.

## 2. Main Components (Bade Hisse)

Application ke teen main hisse hain:

### A. Java Desktop App (Frontend & Backend Logic)
Yeh main application hai jisse user interact karta hai. Yeh **JavaFX** aur **Spring Boot** pe bani hai.
- **UI (User Interface)**: Login, Dashboard, aur Settings yahan handle hote hain (`com.educater.ui`).
- **Auth Service**: Users ke secure login ko handle karta hai.
- **R2 Service**: Cloudflare R2 (cloud storage) pe files upload karne aur manage karne ka kaam karta hai.
- **Mux API**: Live streams banane aur manage karne ke liye Mux ke sath judta hai.
- **Database**: User data store karne ke liye MongoDB aur PostgreSQL ka use karta hai.

### B. Python Video Encoder (Media Processing)
Yeh ek background engine hai jo `videoEncoder/convert.py` mein likha hai. Yeh heavy video processing ka kaam karta hai taaki server/cloud ka kharcha bache.
- **Adaptive Bitrate Streaming (HLS)**: Ek badi video ko alag-alag quality (1080p, 720p, 480p, 360p, 240p) mein todta hai. Taaki slow internet pe bhi video bina buffering ke chale.
- **Hardware Acceleration**: Agar PC mein Nvidia GPU hai (NVENC), toh usko use karke encoding bahut fast kar deta hai.
- **Watchdog Pattern**: Jaise hi video ka ek chota hissa (segment) ban jata hai, yeh turant use Cloudflare R2 par upload kar deta hai, bina poori video ke banne ka wait kiye.

### C. Cloud Infrastructure
- **Cloudflare R2**: Yeh S3 jaisa storage hai jahan saari videos aur assets store hote hain. Isme download (egress) ka cost zero hota hai.
- **Mux**: Live streaming ki API hai.
- **Database**: MongoDB Atlas aur PostgreSQL cloud par data save karte hain.

## 3. Yeh Kaise Kaam Karta Hai? (Workflow)

Maan lijiye aapko ek nayi video upload karni hai:
1. **User Input**: Aap Java app (EduCaster) open karke ek video select karte hain.
2. **Command Pass**: Java app ek command bhejta hai Python script (`convert.py`) ko ki "Bhai, is video ko process karo".
3. **Parallel Encoding**: Python engine **FFmpeg** ka use karke us video ko ek sath alag-alag resolutions (1080p, 720p, etc.) mein convert karna shuru kar deta hai.
4. **Real-time Upload**: Jaise-jaise video ke chote-chote `.ts` (segments) files bante jate hain, Python ka `Watchdog` unko detect karta hai aur turant **Cloudflare R2** par upload kar deta hai.
5. **Completion**: Jab sab kuch ho jata hai, master playlist (`.m3u8` file) upload hoti hai aur Java app aapko notification de deta hai ki "Video ready hai!".

## 4. Technology Stack (Kin cheezon se bana hai?)

- **Languages**: Java 17+, Python 3.9+
- **Desktop UI**: JavaFX (OpenJFX)
- **Cloud Storage**: Cloudflare R2 (AWS SDK v2 via Boto3/Java)
- **Video Engine**: FFmpeg (Python Wrapper)
- **Live Streaming**: Mux API
- **Build Tool**: Maven (`pom.xml`)

## 5. Folder Structure Kaise Hai?

- `src/main/java/com/educater` -> Yahan Java ka saara UI aur backend logic rakha hai.
- `videoEncoder/` -> Yahan Python ki script (`convert.py`), configurations (`r2_config.json`), aur FFmpeg binaries hain.
- `pom.xml` -> Maven configuration jisse Java app build aur run hoti hai.
- `SYSTEM_DESIGN.md` -> Detail mein English mein architecture samjhaya gaya hai.

---
**Summary me bole toh:** EduCaster ek smart desktop tool hai jo aapke computer ki power (CPU/GPU) use karke video ko alag-alag quality me convert karta hai aur saste cloud storage (Cloudflare R2) me seedha bhej deta hai, jisse platform chalane ka kharcha bahut kam ho jata hai!

---

## 6. User Q&A (Aapke Sawal)

**Q: Kya hum Python wali dependency hata kar encoding/transcoding ko seedha Java UI (backend) se kar sakte hain? (Kyunki Python slow lagta hai aur mujhe sirf .m3u8 nahi balki standard .mp4 format me bhi alag-alag quality chahiye)**

**Ans (Haan, Bilkul Ho Sakta Hai!):**

1. **Python Hatana (Direct Java Integration):**
   Hum bilkul Python ko hata sakte hain. Asal mein video encode/compress karne ka kaam **FFmpeg** karta hai (jo C/C++ me bana hai aur bahut fast hai), Python sirf usko commands dene ka kaam kar raha tha. Hum Java mein `ProcessBuilder` ya kisi Java library (jaise `JAVE2` ya `Bramp FFmpeg wrapper`) ka use karke seedha Java se FFmpeg ko control kar sakte hain. Isse Python pe dependency khatam ho jayegi aur app ka architecture simple aur fast ho jayega.

2. **Speed & Performance:**
   Kyunki encoding ka main heavy lifting FFmpeg karta hai, Java se isko run karne par koi speed loss nahi hoga. Balki Java ki better multi-threading se process aur efficient ban sakti hai aur app ka memory management bhi better ho jayega.

3. **MP4 Formats aur Other Qualities:**
   HLS (`.m3u8`) zyadatar live streaming ya chunked streaming ke liye use hota hai. Agar aapko VOD (Video on Demand) ya download karne wali videos chahiye, toh hum FFmpeg ko instruction de sakte hain ki woh HLS banane ke bajaye seedha **standard `.mp4` format** me video output kare. Hum alag-alag qualities (1080p, 720p, 480p) me `.mp4` files bana sakte hain aur unhe Cloudflare R2 pe upload kar sakte hain jise kisi bhi normal video player pe chalaya ja sake.

**Q2: Kya UI mein Queue/Batch processing (with High CPU warning), R2 bucket ke liye Delete/Move options, aur multiple uploads ke sath Pause/Resume ka feature add ho sakta hai?**

**Ans (Bilkul, Ye Saare Advanced Features Java UI Me Add Kiye Ja Sakte Hain!):**

1. **Queue Processing & Batch Processing (Background Task):**
   Java me hum ek **Background Task Manager (Queue)** bana sakte hain. Iska matlab hai ki ek video process/upload ho rahi hogi aur aap app mein doosre kaam kar payenge (UI freeze nahi hoga). Agar aap ek sath bahut saari videos daalte hain (Batch Processing), toh app aapko ek **Warning Prompt** dega: *"Warning: High CPU Usage expected. Minimum requirement: 4-Core CPU / 8GB RAM. Do you want to continue?"*. Aapke "Yes" karne pe hi processing shuru hogi.

2. **Bucket Navigation (Delete & Move Options):**
   Hum Java UI me ek naya "File Manager" ya "Bucket Explorer" tab bana sakte hain. Ye bilkul aapke Windows ke file explorer jaisa dikhega. Wahan se aap R2 bucket ke files/folders ko dekh payenge, aur unhe select karke seedha **Delete** ya doosre folder me **Move** (rename/copy) kar payenge bina Cloudflare ka dashboard khole.

3. **Multiple Simultaneous Uploads & Pause/Resume:**
   EduCaster mein AWS SDK v2 (`S3 Transfer Manager`) ka use hua hai, jo natively **Multipart Upload** support karta hai. Iska use karke hum multiple videos ek sath upload kar sakte hain. Hum UI me har upload ke aage **Pause (⏸️)** aur **Resume (▶️)** ka button de sakte hain. Agar internet disconnect ho jaye ya aap pc band karna chahe, toh upload pause ho jayega aur baad mein wahi se resume ho jayega!

**Q3: Kya Settings mein Cloudflare R2 aur AWS S3 ke beech switch karne ka option, ek Refresh button, aur ek properly organized UI banaya ja sakta hai? (4th Question)**

**Ans (Haan, Ye Ekdum Possible Aur Zaroori Hai!):**

1. **R2 aur AWS S3 Switch Option (Settings):**
   Kyunki Cloudflare R2 aur AWS S3 dono bilkul same "S3 API" architecture use karte hain, UI ke "Settings" tab me hum ek **Dropdown ya Toggle (R2 🔁 AWS S3)** de sakte hain. User jo service select karega, app automatically uska endpoint URL change kar degi. Code mein bahut kam changes ke sath dono platforms perfectly support ho jayenge.

2. **Refresh Button:**
   Bucket Explorer (File Manager) mein ek **Refresh (🔄)** button add karna bahut aasan hai. Ispe click karte hi app cloud se latest files ki list fetch karegi aur UI ko turant update kar degi, taaki aapko hamesha real-time data dikhe.

3. **Properly Organized Modern UI:**
   In saare naye features ko handle karne ke liye hum JavaFX UI ko proper Tabs aur Sections me baant sakte hain:
   - **Dashboard Tab:** Jahan active uploads, CPU warning, aur Pause/Resume ke controls honge.
   - **Cloud Explorer Tab:** Yahan aapki R2/S3 files dikhengi, jisme Refresh, Delete aur Move ke options honge.
   - **Settings Tab:** Yahan API keys aur R2/S3 switch karne ka clean form hoga.

**Q4: Agar hum HLS ki jagah `.mp4` format mein videos transcode aur upload karte hain (jaise `courses/Course_Title/Chapter1/` folder mein), toh frontend player ko kaise pata chalega ki multiple qualities (1080p, 720p) available hain aur wo unhe play kaise karega? (5th Question)**

**Ans:**

Jab hum HLS (`.m3u8`) use karte hain toh usme ek "Master Playlist" file banti hai jo player ko automatically bata deti hai ki konsi quality kahan hai. Lekin standard `.mp4` files mein aisa nahi hota. Isliye, humein apna custom logic use karna padta hai:

1. **Folder aur Naming Structure:**
   App upload karte waqt alag-alag versions ko ek proper naming convention ke sath bucket mein save karti hai:
   - `courses/Course_Title/Chapter1/video_1080p.mp4`
   - `courses/Course_Title/Chapter1/video_720p.mp4`
   - `courses/Course_Title/Chapter1/video_480p.mp4`

2. **Database ka Role:**
   Upload pura hone ke baad, aapka application backend (jaise MongoDB) in teeno `.mp4` URLs ko database me ek single video entry (jaise "Chapter 1 Video") ke roop mein save kar leta hai.

3. **Frontend aur Custom Video Player:**
   Jab user frontend me video chalata hai, toh backend video player ko ek JSON object bhejta hai jisme qualities aur unke URLs hote hain:
   ```json
   {
     "1080p": "https://<bucket_url>/courses/Course_Title/Chapter1/video_1080p.mp4",
     "720p": "https://<bucket_url>/courses/Course_Title/Chapter1/video_720p.mp4",
     "480p": "https://<bucket_url>/courses/Course_Title/Chapter1/video_480p.mp4"
   }
   ```
4. **Player Plugin (e.g., Video.js ya Plyr.io):**
   Aapko frontend mein **Plyr.io** ya **Video.js** jaisa ek modern player use karna hoga. In players me "Quality Selection" (⚙️ icon) ka option diya ja sakta hai (custom plugin ke through). Jaise hi user 720p se 1080p par switch karta hai, player background mein 720p wali video ko pause karta hai aur current time-stamp se seedha 1080p wali `.mp4` URL load karke play kar deta hai. 

**(Note:** HLS internet speed ke hisaab se *automatically* quality change karta hai (Adaptive bitrate). Par `.mp4` me user ko gear icon se *manually* quality select karni padti hai, jaise YouTube pe manually quality change karte hain).

**Q6: Toh kya mujhe apna frontend Admin Panel update karna hoga jahan batana pade ki agar `.m3u8` (HLS) hai toh single link use karo, aur agar `.mp4` hai toh multiple links add karo?**

**Ans (Haan, Ye Ekdum Sahi Approach Hai):**

Haan, aapko apne backend database aur frontend Admin Panel mein thode changes karne honge, kyunki HLS aur MP4 ko player mein render karne ka logic alag hota hai.

1. **Database mein Flag Add Karna:**
   Aapko database mein video entry ke sath ek naya field (jaise `video_format: 'hls'` ya `video_format: 'mp4'`) save karna hoga.
   
2. **Admin Panel UI Update:**
   Admin Panel mein video upload ya link add karne ke form ko dynamic banana hoga:
   - **Agar 'HLS (.m3u8)' format hai:** Toh form mein sirf ek input field show hoga jisme Master Playlist URL (`master.m3u8`) aayega.
   - **Agar 'MP4' format hai:** Toh form mein multiple input fields dikhne chahiye jahan system automatically (ya admin manually) alag-alag qualities ki `.mp4` URLs (1080p, 720p, etc.) save kar sake.
   
3. **Frontend Player Logic (Smart Player):**
   Jab user ke paas video play hone aayegi, toh frontend code us `video_format` flag ko check karega aur player ko uske hisaab se data dega:
   ```javascript
   if (videoData.format === 'hls') {
       // Single URL: Player (like HLS.js) ko seedha .m3u8 file de do
       initHlsPlayer(videoData.hls_url); 
   } else if (videoData.format === 'mp4') {
       // Multiple URLs: Player ko array of sources de do quality selection ke liye
       initMp4Player([
           { src: videoData.mp4_urls['1080p'], size: 1080 },
           { src: videoData.mp4_urls['720p'], size: 720 }
       ]);
   }
   ```
Is tarike se aapka system dono formats (HLS aur MP4) ko support karne ke liye ekdum flexible ho jayega!
