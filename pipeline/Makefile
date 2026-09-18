# Convenience targets for the munichhenge pipeline. Run from pipeline/.
PBF_URL ?= https://download.geofabrik.de/europe/germany/bayern/oberbayern-latest.osm.pbf
RAW_DIR ?= ../raw
PBF     ?= $(RAW_DIR)/oberbayern-latest.osm.pbf
OUT     ?= ../data
PY      ?= python

.PHONY: venv download build debug test clean

venv:
	python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt

download:
	mkdir -p $(RAW_DIR)
	curl -L --fail --progress-bar -o $(PBF) $(PBF_URL)
	curl -L --fail -s -o $(PBF).md5 $(PBF_URL).md5 && cd $(RAW_DIR) && \
	    sed 's#  .*oberbayern-latest.osm.pbf#  oberbayern-latest.osm.pbf#' oberbayern-latest.osm.pbf.md5 | md5sum -c -

build:
	$(PY) -m pipeline.run --pbf $(PBF) --out $(OUT)

debug:
	$(PY) -m pipeline.run --pbf $(PBF) --out $(OUT) --debug-geojson $(OUT)/debug

test:
	$(PY) -m pytest

clean:
	rm -rf .pytest_cache pipeline/__pycache__ tests/__pycache__
