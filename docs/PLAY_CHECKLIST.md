# Google Play release checklist

See DEPLOYMENT.md for the full list. Headline items:

- [ ] Privacy policy URL, Terms URL, Community Guidelines URL published
- [ ] Data Safety form completed and matches the actual data flows
- [ ] Account deletion (in-app + web) tested end-to-end
- [ ] Content rating questionnaire + 18+ age gate tested (under-18 registration rejected server-side)
- [ ] Report / block / suspend / ban tested from the app and from admin
- [ ] Play Billing products created and server-side verification tested (never trust the client alone)
- [ ] AdMob placements checked: never during an active call
- [ ] TURN fallback tested (P2P blocked network → call still connects via TURN)
- [ ] Two-device live call tested: connect, mute, camera off, flip, NEXT, report, block, reconnect after network switch
- [ ] Closed testing track, then staged rollout
