#!/usr/bin/env python3
"""
Create Spanish translation (es_es.json) from en_us.json for Power Levelling.
Run with: python tools/create_spanish_translation.py
"""
import json
from pathlib import Path

EN_PATH = Path(__file__).parent.parent / "src/main/resources/assets/powerlevelling/lang/en_us.json"
ES_PATH = Path(__file__).parent.parent / "src/main/resources/assets/powerlevelling/lang/es_es.json"

# Spanish translations for all English keys
# Format: "english_value": "spanish_translation"
# Placeholders like %s, %1$s, %2$s are preserved exactly.
TRANSLATIONS = {