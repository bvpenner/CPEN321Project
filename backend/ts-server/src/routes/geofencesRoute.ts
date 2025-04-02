import express from 'express';
import { fetchGeofencesControl } from '../controllers/geofencesController';

const router = express.Router();

router.get('/fetchGeofences', fetchGeofencesControl);

export default router;
