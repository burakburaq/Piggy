[![English](https://img.shields.io/badge/README-English-blue.svg?style=for-the-badge)](README.md)
[![Türkçe](https://img.shields.io/badge/README-Türkçe-red.svg?style=for-the-badge)](README.tr.md)

_Kapak görseli yakında eklenecek._

### **Açıklama**
Piggy, oynanış mekaniklerine ve HUD iyileştirmelerine odaklanan bir Minecraft Fabric modudur. İstemci tarafında kurulabilir ve yeni eşya veya blok eklemeden mevcut vanilla kayıtlarıyla kullanılabilir. Özelliklerini, ayarlarını ve kısayollarını aşağıda inceleyebilirsiniz. Modu kullanmak için [Fabric API](https://modrinth.com/mod/fabric-api) gereklidir.

[Kullanım koşulları](LICENSE) için buraya bakın.

[![Download on Modrinth](https://img.shields.io/badge/Download-Modrinth-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/) 
[![Download on CurseForge](https://img.shields.io/badge/Download-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods)
[![GitHub Repository](https://img.shields.io/badge/Source-Github-8957E5?style=for-the-badge&logo=github&logoColor=white)](./)

### **Görseller**
_Görseller yakında eklenecek._

## **Geliştirme ve doğrulama**
Java 25 gereklidir.

```powershell
# Projeyi derle
.\gradlew.bat --gradle-user-home .gradle-user build
# İstemci oyun testini çalıştır
.\gradlew.bat --gradle-user-home .gradle-user runClientGameTest -PclientTest=MigrationSmokeGameTest
# Elle test etmek için geliştirme istemcisini başlat
.\gradlew.bat --gradle-user-home .gradle-user runClient
```

Oyun testleri, çalışan bir Minecraft istemcisinde ve sunucusunda modun davranışını kontrol eder. Dağıtım JAR dosyası test sınıflarını içermez.

> Java paket önekinin ileride değiştirilmesi planlanmaktadır.

## **Ayarlar ve kısayollar**

- **O:** Piggy'nin kategori ve ayarlar menüsünü açar. Bu tuşu Kontroller ekranından değiştirebilirsiniz.
- **F4:** Serbest kamerayı açıp kapatır. Bu tuşu Kontroller ekranından değiştirebilirsiniz.
- Yardımcı özellikleri O → Arayüz / Etkileşimli altında ayrı ayrı yapılandırabilirsiniz. Tercihler `config/piggy-quality.json` dosyasında saklanır.


## **Özellikler**

### F3 HUD
Aşağıdaki bilgileri kategoriler halinde düzenler ve diğer tüm bilgileri gizler:
- FPS, bellek, CPU ve grafik ölçümleri.
- Koordinatlar, bakış yönü, biyom ve boyut.
- Ping ve mevcut olduğunda gerçek sunucu TPS değeri.
Minecraft'ın varsayılan **F3** menüsünün yerini alır.

### Shulker önizlemesi
İçeriğini önizlemek için envanterinizdeki bir shulker kutusunun üzerine gelin.

### Elytra roket sayaçları
Elytra ile süzülürken roket simgelerini ve sayılarını uçuş sürelerine göre gösterir.

### Yiyecek, açlık ve sağlık önizlemesi
Envanterdeki yiyeceklerin araç ipuçları besin değerlerini gösterir. Açlık ve sağlık çubukları; doygunluk, açlık ve sağlık yenilenmesini önizler.

### Ok sayaçları
Yay veya arbalet tutarken ok türlerini ve sayılarını atış sırasına göre gösterir.

### Hız göstergesi
Yatay hızı b/s (blok/saniye) cinsinden gösterir ve hız 3,5 b/s'nin altına düştüğünde gizlenir. Konumunu ayarlardan değiştirebilirsiniz. Varsayılan olarak atanmış bir kısayolu yoktur.


### Bunny hopping
Doğru zamanlanmış art arda zıplamalar ve havada yön verme yoluyla hareket ivmesi kazandırır. Otomatik bunny hop etkin olduğunda zıplama tuşunu basılı tutabilirsiniz; aksi halde her zıplama için tuşa yeniden basmanız gerekir.

> Bunny hopping varsayılan olarak devre dışıdır. Mod ayarlarından değiştirebilirsiniz; tercih `config/piggy-client.json` dosyasında saklanır.
>
> Sunucu sahipleri, bunny hop sınırlarını `config/piggy-server.json` dosyasında belirleyebilir. Değişiklikler sunucu yeniden başlatıldıktan sonra geçerli olur.

### Daha hızlı blok yerleştirme
Minecraft'ın blok yerleştirmeleri arasındaki varsayılan gecikmesini 4 tick'ten 2 tick'e düşürür.

### Sağ tık zamanlaması
Eşya ve sunucu kurallarını koruyarak blok yerleştirme, su kovası kullanma, ekim yapma ve havai fişek kullanma gibi etkileşimlere uygun tekrarlama gecikmeleri uygular.

### Hızlı eşya ve blok değişimi
Envanterde yedeği varsa kırılan bir aleti, yerleştirilerek tükenen bir bloğu veya tüketilen bir totemi yenisiyle değiştirir. Alet seçiminde aynı tür ve malzemeye öncelik verir.

### Anti-AFK
Etkinleştirildiğinde kısa süreli, rastgele hareket girdileri üretir. Hareket etme, zıplama veya eğilme girdisi özelliği otomatik olarak durdurur.

### Eşli kapılar
Yan yana duran aynı tür ahşap kapıları birlikte açar veya kapatır. Demir kapılar ve redstone ile çalıştırılan kapılar kapsam dışındadır.

### Köprü kurma yardımı
İlk geçerli blok yerleştirmesinden sonra, blok yerleştirmeyi bırakana kadar köprü aynı yükseklikte ve yönde kalır.

## Diğer
Bu özellikler, bunlara izin vermeyen sunucularda devre dışı bırakılır.

### Bunny hopping
Doğru zamanlanmış art arda zıplamalar ve havada yön verme yoluyla hareket ivmesi kazandırır. Otomatik bunny hop etkin olduğunda zıplama tuşunu basılı tutabilirsiniz; aksi halde her zıplama için tuşa yeniden basmanız gerekir.

> Bunny hopping varsayılan olarak devre dışıdır. Mod ayarlarından değiştirebilirsiniz; tercih `config/piggy-client.json` dosyasında saklanır.
>
> Sunucu sahipleri, bunny hop sınırlarını `config/piggy-server.json` dosyasında belirleyebilir. Değişiklikler sunucu yeniden başlatıldıktan sonra geçerli olur.

### Oyuncuları duvarların arkasından görme
Blokların kendisini gizlemeden diğer oyuncuları blokların arkasından gösterir.

### Serbest kamera
Oyuncu yerinde sabit kalırken kamerayı bağımsız olarak hareket ettirir. Kamerayı daha hızlı hareket ettirmek için **Ctrl** tuşunu basılı tutun. Kamera hareketi, sunucuya oyuncu hareketi olarak gönderilmez.

## License
Piggy is licensed under the [MIT License](LICENSE).
© 2026 burakburaq.
