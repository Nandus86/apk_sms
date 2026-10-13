# Flash SMS Gateway (Classe 0)

Aplicativo Android nativo para envio e testes de **Flash SMS (Classe 0)** — mensagens prioritárias que aparecem instantaneamente na tela do dispositivo receptor.

## 🚀 Métodos de Disparo Disponíveis

1. **PDU Classe 0 (Reflexão Java / DCS=0x10)**:
   - Constrói o PDU com Data Coding Scheme (DCS) configurado para Classe 0.
   - Gera o PDU HEX para depuração/análise.
   - Tenta despachar via chamada interna oculta `sendRawPdu` de `SmsManager`.
   *(Nota: No Android 9+ moderno, chamadas a APIs ocultas sem assinatura de sistema podem ser bloqueadas pelo SO).*

2. **Comandos AT no Modem (Requer Root)**:
   - Executa comandos diretamente no modem serial via shell `su` (ex: `/dev/smd7` em chipsets Qualcomm).
   - Configura `AT+CSMP=17,167,0,16` (DCS 16 = Classe 0) e despacha via `AT+CMGS`.

3. **SMS Padrão (Classe 1)**:
   - Envio convencional via API pública do Android (`sendTextMessage`) para validar funcionamento da rede e do chip SIM.

## 🛠️ Compilação Automática (CI/CD)

O projeto possui um workflow configurado no GitHub Actions (`.github/workflows/build-apk.yml`) que compila o APK a cada push nas branches `main` ou `master`.

Para baixar o APK:
1. Acesse a aba **Actions** no repositório do GitHub.
2. Clique no último workflow executado.
3. Baixe o artefato **FlashSMS-APK**.
