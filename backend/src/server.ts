import { app } from './app.js'
import { env } from './config/env.js'
import { startRecoverySweep } from './utils/recovery.js'

app.listen(env.APP_PORT, () => {
  console.log(`Backend running on http://localhost:${env.APP_PORT}`)
  startRecoverySweep()
})
