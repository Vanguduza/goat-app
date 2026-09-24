#!/usr/bin/env python3
"""Canonical owner-authorized Farm OS Feature-ID catalogue."""
from __future__ import annotations
from copy import deepcopy

CATALOG_STATUS = "CANONICAL_OWNER_AUTHORIZED"
CATALOG_AUTHORITY = "owner-directed deterministic completion plan, 24 September 2026"
SCOPE_LAW = "ALL_545_REGISTERED_SCREENS_MUST_HAVE_EXACTLY_ONE_PRIMARY_MANDATORY_FEATURE"

FEATURES = [
  {
    "feature_id": "FTR-GLOBAL-001",
    "module": "global",
    "name": "Session entry and resilience",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-GLOBAL-001",
      "FOS-GLOBAL-002",
      "FOS-GLOBAL-016",
      "FOS-GLOBAL-018"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GLOBAL-002",
    "module": "global",
    "name": "Account lifecycle",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-GLOBAL-003",
      "FOS-GLOBAL-004",
      "FOS-GLOBAL-019",
      "FOS-GLOBAL-020"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GLOBAL-003",
    "module": "global",
    "name": "Farm onboarding and membership",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-GLOBAL-005",
      "FOS-GLOBAL-006",
      "FOS-GLOBAL-007",
      "FOS-GLOBAL-008",
      "FOS-GLOBAL-009",
      "FOS-GLOBAL-014",
      "FOS-GLOBAL-015"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GLOBAL-004",
    "module": "global",
    "name": "Device permission onboarding",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-GLOBAL-010",
      "FOS-GLOBAL-011",
      "FOS-GLOBAL-012",
      "FOS-GLOBAL-013"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GLOBAL-005",
    "module": "global",
    "name": "Farm access revocation",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-GLOBAL-017"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-001",
    "module": "home",
    "name": "Farm command centre",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-001",
      "FOS-HOME-003",
      "FOS-HOME-004",
      "FOS-HOME-005",
      "FOS-HOME-012",
      "FOS-HOME-012-A",
      "FOS-HOME-012-B",
      "FOS-HOME-012-C",
      "FOS-HOME-012-D",
      "FOS-HOME-012-E",
      "FOS-HOME-012-F",
      "FOS-HOME-012-G",
      "FOS-HOME-012-H"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-002",
    "module": "home",
    "name": "Species navigation",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-002"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-003",
    "module": "home",
    "name": "Global discovery",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-006",
      "FOS-HOME-007"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-004",
    "module": "home",
    "name": "Quick capture",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-005",
    "module": "home",
    "name": "Sync status entry",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-006",
    "module": "home",
    "name": "Farm switching",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HOME-007",
    "module": "home",
    "name": "Notification centre",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-HOME-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-001",
    "module": "goat",
    "name": "Goat identity and records",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-001",
      "FOS-GOAT-002",
      "FOS-GOAT-003",
      "FOS-GOAT-004",
      "FOS-GOAT-005",
      "FOS-GOAT-006",
      "FOS-GOAT-007",
      "FOS-GOAT-008",
      "FOS-GOAT-009",
      "FOS-GOAT-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-002",
    "module": "goat",
    "name": "Growth and body condition",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-011",
      "FOS-GOAT-012",
      "FOS-GOAT-013",
      "FOS-GOAT-014",
      "FOS-GOAT-015",
      "FOS-GOAT-016"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-003",
    "module": "goat",
    "name": "Milk and lactation",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-017",
      "FOS-GOAT-018",
      "FOS-GOAT-019",
      "FOS-GOAT-020",
      "FOS-GOAT-021"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-004",
    "module": "goat",
    "name": "Goat health and parasite control",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-022",
      "FOS-GOAT-023",
      "FOS-GOAT-024",
      "FOS-GOAT-025",
      "FOS-GOAT-026",
      "FOS-GOAT-027",
      "FOS-GOAT-028",
      "FOS-GOAT-029"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-005",
    "module": "goat",
    "name": "Doe reproduction and pregnancy",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-030",
      "FOS-GOAT-031",
      "FOS-GOAT-032",
      "FOS-GOAT-033",
      "FOS-GOAT-034",
      "FOS-GOAT-035",
      "FOS-GOAT-036"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-006",
    "module": "goat",
    "name": "Kidding and offspring",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-037",
      "FOS-GOAT-038",
      "FOS-GOAT-039",
      "FOS-GOAT-040",
      "FOS-GOAT-041",
      "FOS-GOAT-042",
      "FOS-GOAT-043"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-007",
    "module": "goat",
    "name": "Pedigree and breeding decisions",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-044",
      "FOS-GOAT-045",
      "FOS-GOAT-046"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-008",
    "module": "goat",
    "name": "Groups movement and identification",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-047",
      "FOS-GOAT-048",
      "FOS-GOAT-049",
      "FOS-GOAT-050"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-009",
    "module": "goat",
    "name": "Lifecycle status and exits",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-051",
      "FOS-GOAT-052",
      "FOS-GOAT-053",
      "FOS-GOAT-054"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GOAT-010",
    "module": "goat",
    "name": "Goat reporting",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-GOAT-055"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-001",
    "module": "rabbit",
    "name": "Rabbit identity and breeding stock",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-001",
      "FOS-RABBIT-002",
      "FOS-RABBIT-003",
      "FOS-RABBIT-004",
      "FOS-RABBIT-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-002",
    "module": "rabbit",
    "name": "Cage and occupancy management",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-006",
      "FOS-RABBIT-007",
      "FOS-RABBIT-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-003",
    "module": "rabbit",
    "name": "Breeding pregnancy and kindling",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-009",
      "FOS-RABBIT-010",
      "FOS-RABBIT-011",
      "FOS-RABBIT-012",
      "FOS-RABBIT-013",
      "FOS-RABBIT-014",
      "FOS-RABBIT-015",
      "FOS-RABBIT-016",
      "FOS-RABBIT-017"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-004",
    "module": "rabbit",
    "name": "Litter and kit lifecycle",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-018",
      "FOS-RABBIT-019",
      "FOS-RABBIT-020",
      "FOS-RABBIT-021",
      "FOS-RABBIT-022",
      "FOS-RABBIT-023",
      "FOS-RABBIT-024",
      "FOS-RABBIT-025"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-005",
    "module": "rabbit",
    "name": "Market waitlist reservations and sales",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-026",
      "FOS-RABBIT-027",
      "FOS-RABBIT-028",
      "FOS-RABBIT-029",
      "FOS-RABBIT-030"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-006",
    "module": "rabbit",
    "name": "Rabbit lineage growth and health",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-031",
      "FOS-RABBIT-032",
      "FOS-RABBIT-033",
      "FOS-RABBIT-034",
      "FOS-RABBIT-035"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-RABBIT-007",
    "module": "rabbit",
    "name": "Rabbit reporting",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-RABBIT-036"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-001",
    "module": "sheep",
    "name": "Sheep identity and flock management",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-001",
      "FOS-SHEEP-002",
      "FOS-SHEEP-003",
      "FOS-SHEEP-004",
      "FOS-SHEEP-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-002",
    "module": "sheep",
    "name": "Growth condition and parasite scoring",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-006",
      "FOS-SHEEP-007",
      "FOS-SHEEP-008",
      "FOS-SHEEP-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-003",
    "module": "sheep",
    "name": "Joining pregnancy and lambing",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-010",
      "FOS-SHEEP-011",
      "FOS-SHEEP-012",
      "FOS-SHEEP-013",
      "FOS-SHEEP-014",
      "FOS-SHEEP-015",
      "FOS-SHEEP-016",
      "FOS-SHEEP-017"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-004",
    "module": "sheep",
    "name": "Wool and fleece production",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-018",
      "FOS-SHEEP-019",
      "FOS-SHEEP-020",
      "FOS-SHEEP-021",
      "FOS-SHEEP-022"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-005",
    "module": "sheep",
    "name": "Sheep welfare and health",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-023",
      "FOS-SHEEP-024",
      "FOS-SHEEP-025"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-006",
    "module": "sheep",
    "name": "Lineage movement and lifecycle",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-026",
      "FOS-SHEEP-027",
      "FOS-SHEEP-028",
      "FOS-SHEEP-029",
      "FOS-SHEEP-030",
      "FOS-SHEEP-031"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SHEEP-007",
    "module": "sheep",
    "name": "Sheep reporting",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-SHEEP-032"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-001",
    "module": "cattle",
    "name": "Cattle identity and herd management",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-001",
      "FOS-CATTLE-002",
      "FOS-CATTLE-003",
      "FOS-CATTLE-004",
      "FOS-CATTLE-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-002",
    "module": "cattle",
    "name": "Growth and condition",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-006",
      "FOS-CATTLE-007",
      "FOS-CATTLE-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-003",
    "module": "cattle",
    "name": "Breeding pregnancy and calving",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-009",
      "FOS-CATTLE-010",
      "FOS-CATTLE-011",
      "FOS-CATTLE-012",
      "FOS-CATTLE-013",
      "FOS-CATTLE-014",
      "FOS-CATTLE-015",
      "FOS-CATTLE-016",
      "FOS-CATTLE-017"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-004",
    "module": "cattle",
    "name": "Dairy production and udder health",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-018",
      "FOS-CATTLE-019",
      "FOS-CATTLE-020",
      "FOS-CATTLE-021",
      "FOS-CATTLE-022",
      "FOS-CATTLE-023",
      "FOS-CATTLE-024"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-005",
    "module": "cattle",
    "name": "Beef and feedlot production",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-025",
      "FOS-CATTLE-026",
      "FOS-CATTLE-027",
      "FOS-CATTLE-028",
      "FOS-CATTLE-029"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-006",
    "module": "cattle",
    "name": "Movement lineage health and lifecycle",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-030",
      "FOS-CATTLE-031",
      "FOS-CATTLE-032",
      "FOS-CATTLE-033",
      "FOS-CATTLE-034",
      "FOS-CATTLE-035"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CATTLE-007",
    "module": "cattle",
    "name": "Cattle reporting",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-CATTLE-036"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-POULTRY-001",
    "module": "poultry",
    "name": "Poultry configuration flocks and housing",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-POULTRY-001",
      "FOS-POULTRY-002",
      "FOS-POULTRY-003",
      "FOS-POULTRY-004",
      "FOS-POULTRY-005",
      "FOS-POULTRY-006",
      "FOS-POULTRY-007",
      "FOS-POULTRY-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-POULTRY-002",
    "module": "poultry",
    "name": "Mortality eggs and feed performance",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-POULTRY-009",
      "FOS-POULTRY-010",
      "FOS-POULTRY-011",
      "FOS-POULTRY-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-POULTRY-003",
    "module": "poultry",
    "name": "Vaccination and biosecurity",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-POULTRY-013",
      "FOS-POULTRY-014",
      "FOS-POULTRY-015",
      "FOS-POULTRY-016"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-POULTRY-004",
    "module": "poultry",
    "name": "Hatchery and incubation",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-POULTRY-017",
      "FOS-POULTRY-018",
      "FOS-POULTRY-019",
      "FOS-POULTRY-020",
      "FOS-POULTRY-021"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-POULTRY-005",
    "module": "poultry",
    "name": "Flock movement closeout and health",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-POULTRY-022",
      "FOS-POULTRY-023",
      "FOS-POULTRY-024",
      "FOS-POULTRY-025",
      "FOS-POULTRY-026"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-POULTRY-006",
    "module": "poultry",
    "name": "Poultry reporting",
    "mandatory": true,
    "completion_phase": 8,
    "screen_ids": [
      "FOS-POULTRY-027"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-001",
    "module": "health",
    "name": "Health triage observations and cases",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-001",
      "FOS-HEALTH-002",
      "FOS-HEALTH-003",
      "FOS-HEALTH-004",
      "FOS-HEALTH-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-002",
    "module": "health",
    "name": "Treatment and withdrawal control",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-006",
      "FOS-HEALTH-007",
      "FOS-HEALTH-008",
      "FOS-HEALTH-009",
      "FOS-HEALTH-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-003",
    "module": "health",
    "name": "Vaccination management",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-011",
      "FOS-HEALTH-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-004",
    "module": "health",
    "name": "Formulary governance",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-013",
      "FOS-HEALTH-014"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-005",
    "module": "health",
    "name": "Protocol packs and application",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-015",
      "FOS-HEALTH-016",
      "FOS-HEALTH-017",
      "FOS-HEALTH-018",
      "FOS-HEALTH-019"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-006",
    "module": "health",
    "name": "Veterinary visits",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-020",
      "FOS-HEALTH-021",
      "FOS-HEALTH-022"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-007",
    "module": "health",
    "name": "Laboratory results",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-023",
      "FOS-HEALTH-024",
      "FOS-HEALTH-025"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-008",
    "module": "health",
    "name": "Clinical reference and emergency guidance",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-026",
      "FOS-HEALTH-027",
      "FOS-HEALTH-028"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-HEALTH-009",
    "module": "health",
    "name": "Health timeline and reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-HEALTH-029",
      "FOS-HEALTH-030"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-TASK-001",
    "module": "task",
    "name": "Task board and task lifecycle",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-TASK-001",
      "FOS-TASK-002",
      "FOS-TASK-003",
      "FOS-TASK-004",
      "FOS-TASK-005",
      "FOS-TASK-006",
      "FOS-TASK-007",
      "FOS-TASK-008",
      "FOS-TASK-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-TASK-002",
    "module": "task",
    "name": "Scheduling calendar and recurrence",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-TASK-010",
      "FOS-TASK-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-TASK-003",
    "module": "task",
    "name": "Task evidence and attachments",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-TASK-012",
      "FOS-TASK-013"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-TASK-004",
    "module": "task",
    "name": "Generated lifecycle and protocol tasks",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-TASK-014",
      "FOS-TASK-015"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-INV-001",
    "module": "inv",
    "name": "Inventory catalogue and discovery",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-INV-001",
      "FOS-INV-002",
      "FOS-INV-003",
      "FOS-INV-004",
      "FOS-INV-017"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-INV-002",
    "module": "inv",
    "name": "Receiving and lot traceability",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-INV-005",
      "FOS-INV-006",
      "FOS-INV-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-INV-003",
    "module": "inv",
    "name": "Issuing FEFO and expiry",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-INV-007",
      "FOS-INV-008",
      "FOS-INV-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-INV-004",
    "module": "inv",
    "name": "Reorder and supplier binding",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-INV-011",
      "FOS-INV-012",
      "FOS-INV-013",
      "FOS-INV-018"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-INV-005",
    "module": "inv",
    "name": "Stock control and reconciliation",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-INV-014",
      "FOS-INV-015",
      "FOS-INV-016"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-INV-006",
    "module": "inv",
    "name": "Inventory reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-INV-019"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FEED-001",
    "module": "feed",
    "name": "Feed inventory",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-FEED-001",
      "FOS-FEED-002",
      "FOS-FEED-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FEED-002",
    "module": "feed",
    "name": "Feed planning and schedules",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-FEED-004",
      "FOS-FEED-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FEED-003",
    "module": "feed",
    "name": "Feed consumption and costing",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-FEED-006",
      "FOS-FEED-007"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FEED-004",
    "module": "feed",
    "name": "Ration formulation and analysis",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-FEED-008",
      "FOS-FEED-009",
      "FOS-FEED-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FEED-005",
    "module": "feed",
    "name": "Feed alerts and reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-FEED-011",
      "FOS-FEED-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-WATER-001",
    "module": "water",
    "name": "Water assets and points",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-WATER-001",
      "FOS-WATER-002",
      "FOS-WATER-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-WATER-002",
    "module": "water",
    "name": "Water consumption quality and inspection",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-WATER-004",
      "FOS-WATER-005",
      "FOS-WATER-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-WATER-003",
    "module": "water",
    "name": "Water issues maintenance and history",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-WATER-007",
      "FOS-WATER-008",
      "FOS-WATER-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-WATER-004",
    "module": "water",
    "name": "Water reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-WATER-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PASTURE-001",
    "module": "pasture",
    "name": "Paddock inventory",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-PASTURE-001",
      "FOS-PASTURE-002",
      "FOS-PASTURE-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PASTURE-002",
    "module": "pasture",
    "name": "Grazing rotation movement and rest",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-PASTURE-004",
      "FOS-PASTURE-005",
      "FOS-PASTURE-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PASTURE-003",
    "module": "pasture",
    "name": "Capacity condition history and mapping",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-PASTURE-007",
      "FOS-PASTURE-008",
      "FOS-PASTURE-009",
      "FOS-PASTURE-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PASTURE-004",
    "module": "pasture",
    "name": "Pasture reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-PASTURE-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GROUP-001",
    "module": "group",
    "name": "Group definition and membership",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-GROUP-001",
      "FOS-GROUP-002",
      "FOS-GROUP-003",
      "FOS-GROUP-004",
      "FOS-GROUP-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GROUP-002",
    "module": "group",
    "name": "Group census movement and health",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-GROUP-006",
      "FOS-GROUP-007",
      "FOS-GROUP-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GROUP-003",
    "module": "group",
    "name": "Group timeline",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-GROUP-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-LABOUR-001",
    "module": "labour",
    "name": "Workforce directory",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-LABOUR-001",
      "FOS-LABOUR-002",
      "FOS-LABOUR-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-LABOUR-002",
    "module": "labour",
    "name": "Assignment worklog and availability",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-LABOUR-004",
      "FOS-LABOUR-005",
      "FOS-LABOUR-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-LABOUR-003",
    "module": "labour",
    "name": "Labour cost and workload",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-LABOUR-007",
      "FOS-LABOUR-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-LABOUR-004",
    "module": "labour",
    "name": "Labour reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-LABOUR-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ASSET-001",
    "module": "asset",
    "name": "Asset register",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-ASSET-001",
      "FOS-ASSET-002",
      "FOS-ASSET-003",
      "FOS-ASSET-004"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ASSET-002",
    "module": "asset",
    "name": "Usage maintenance and service",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-ASSET-005",
      "FOS-ASSET-006",
      "FOS-ASSET-007",
      "FOS-ASSET-008",
      "FOS-ASSET-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ASSET-003",
    "module": "asset",
    "name": "Asset documents and media",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-ASSET-010",
      "FOS-ASSET-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ASSET-004",
    "module": "asset",
    "name": "Asset reporting",
    "mandatory": true,
    "completion_phase": 7,
    "screen_ids": [
      "FOS-ASSET-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FIN-001",
    "module": "fin",
    "name": "Financial transactions",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-FIN-001",
      "FOS-FIN-002",
      "FOS-FIN-003",
      "FOS-FIN-004",
      "FOS-FIN-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FIN-002",
    "module": "fin",
    "name": "Profitability and cost attribution",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-FIN-006",
      "FOS-FIN-007",
      "FOS-FIN-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FIN-003",
    "module": "fin",
    "name": "Cashflow budget and variance",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-FIN-009",
      "FOS-FIN-010",
      "FOS-FIN-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-FIN-004",
    "module": "fin",
    "name": "Finance reporting and export",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-FIN-012",
      "FOS-FIN-013"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SALES-001",
    "module": "sales",
    "name": "Customer management",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-SALES-001",
      "FOS-SALES-002",
      "FOS-SALES-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SALES-002",
    "module": "sales",
    "name": "Orders and sales execution",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-SALES-004",
      "FOS-SALES-005",
      "FOS-SALES-006",
      "FOS-SALES-007",
      "FOS-SALES-008",
      "FOS-SALES-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SALES-003",
    "module": "sales",
    "name": "Fulfilment and sales history",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-SALES-010",
      "FOS-SALES-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SALES-004",
    "module": "sales",
    "name": "Sales reporting",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-SALES-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PROC-001",
    "module": "proc",
    "name": "Supplier management",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-PROC-001",
      "FOS-PROC-002",
      "FOS-PROC-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PROC-002",
    "module": "proc",
    "name": "Purchase orders and receipt",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-PROC-004",
      "FOS-PROC-005",
      "FOS-PROC-006",
      "FOS-PROC-007"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PROC-003",
    "module": "proc",
    "name": "Purchase inventory integration",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-PROC-008",
      "FOS-PROC-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-PROC-004",
    "module": "proc",
    "name": "Procurement reporting",
    "mandatory": true,
    "completion_phase": 9,
    "screen_ids": [
      "FOS-PROC-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CAP-001",
    "module": "cap",
    "name": "Capacity overview",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-CAP-001"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CAP-002",
    "module": "cap",
    "name": "Resource capacity models",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-CAP-002",
      "FOS-CAP-003",
      "FOS-CAP-004",
      "FOS-CAP-005",
      "FOS-CAP-006",
      "FOS-CAP-007",
      "FOS-CAP-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-CAP-003",
    "module": "cap",
    "name": "Capacity forecast and alerts",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-CAP-009",
      "FOS-CAP-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GEN-001",
    "module": "gen",
    "name": "Pedigree and parentage",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-GEN-001",
      "FOS-GEN-002",
      "FOS-GEN-003",
      "FOS-GEN-004"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GEN-002",
    "module": "gen",
    "name": "Relationship and inbreeding analysis",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-GEN-005",
      "FOS-GEN-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GEN-003",
    "module": "gen",
    "name": "Mate and candidate decisions",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-GEN-007",
      "FOS-GEN-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-GEN-004",
    "module": "gen",
    "name": "Genetic warnings and reporting",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-GEN-009",
      "FOS-GEN-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AN-001",
    "module": "an",
    "name": "Farm and species performance",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AN-001",
      "FOS-AN-002"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AN-002",
    "module": "an",
    "name": "Biological and production analytics",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AN-003",
      "FOS-AN-004",
      "FOS-AN-005",
      "FOS-AN-006",
      "FOS-AN-007",
      "FOS-AN-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AN-003",
    "module": "an",
    "name": "Financial analytics",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AN-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AN-004",
    "module": "an",
    "name": "Benchmarking",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AN-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AN-005",
    "module": "an",
    "name": "Anomaly detection and forecasts",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AN-011",
      "FOS-AN-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AN-006",
    "module": "an",
    "name": "Insight evidence drilldown",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AN-013",
      "FOS-AN-014"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SIM-001",
    "module": "sim",
    "name": "Simulation workspace",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-SIM-001"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SIM-002",
    "module": "sim",
    "name": "Domain scenario modelling",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-SIM-002",
      "FOS-SIM-003",
      "FOS-SIM-004",
      "FOS-SIM-005",
      "FOS-SIM-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SIM-003",
    "module": "sim",
    "name": "Scenario comparison and persistence",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-SIM-007",
      "FOS-SIM-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SIM-004",
    "module": "sim",
    "name": "Simulation reporting",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-SIM-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SEARCH-001",
    "module": "search",
    "name": "Search query results and filtering",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SEARCH-001",
      "FOS-SEARCH-002",
      "FOS-SEARCH-003",
      "FOS-SEARCH-004"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SEARCH-002",
    "module": "search",
    "name": "Offline and degraded search",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SEARCH-005",
      "FOS-SEARCH-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SEARCH-003",
    "module": "search",
    "name": "RFID QR and barcode lookup",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SEARCH-007",
      "FOS-SEARCH-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SEARCH-004",
    "module": "search",
    "name": "Recent searches",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SEARCH-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AI-001",
    "module": "ai",
    "name": "Copilot conversation",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AI-001",
      "FOS-AI-002",
      "FOS-AI-003"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AI-002",
    "module": "ai",
    "name": "Insights and evidence",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AI-004",
      "FOS-AI-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AI-003",
    "module": "ai",
    "name": "Suggested action review and anomaly review",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AI-006",
      "FOS-AI-007",
      "FOS-AI-008"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AI-004",
    "module": "ai",
    "name": "Model provider connection",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AI-009",
      "FOS-AI-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-AI-005",
    "module": "ai",
    "name": "AI privacy controls and degraded state",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-AI-011",
      "FOS-AI-012"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-REPORT-001",
    "module": "report",
    "name": "Reports hub",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-REPORT-001"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-REPORT-002",
    "module": "report",
    "name": "Domain reporting",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-REPORT-002",
      "FOS-REPORT-003",
      "FOS-REPORT-004",
      "FOS-REPORT-005",
      "FOS-REPORT-006",
      "FOS-REPORT-007",
      "FOS-REPORT-008",
      "FOS-REPORT-009",
      "FOS-REPORT-010"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-REPORT-003",
    "module": "report",
    "name": "Export and generated documents",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-REPORT-011",
      "FOS-REPORT-012",
      "FOS-REPORT-013"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-REPORT-004",
    "module": "report",
    "name": "Share and print",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-REPORT-014"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-REPORT-005",
    "module": "report",
    "name": "Certificates",
    "mandatory": true,
    "completion_phase": 10,
    "screen_ids": [
      "FOS-REPORT-015"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-001",
    "module": "admin",
    "name": "Settings and farm profile",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-001",
      "FOS-ADMIN-002"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-002",
    "module": "admin",
    "name": "Membership roles and permissions",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-003",
      "FOS-ADMIN-004",
      "FOS-ADMIN-005",
      "FOS-ADMIN-006"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-003",
    "module": "admin",
    "name": "Species locations units and currency",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-007",
      "FOS-ADMIN-008",
      "FOS-ADMIN-009",
      "FOS-ADMIN-010",
      "FOS-ADMIN-011"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-004",
    "module": "admin",
    "name": "Notification sync and search settings",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-012",
      "FOS-ADMIN-013",
      "FOS-ADMIN-014"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-005",
    "module": "admin",
    "name": "Integrations and hardware",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-015",
      "FOS-ADMIN-016",
      "FOS-ADMIN-017",
      "FOS-ADMIN-018"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-006",
    "module": "admin",
    "name": "AI provider administration",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-019"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-007",
    "module": "admin",
    "name": "Security and sessions",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-020",
      "FOS-ADMIN-021"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-008",
    "module": "admin",
    "name": "Data export and restore",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-022",
      "FOS-ADMIN-023"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-009",
    "module": "admin",
    "name": "Audit log",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-024"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ADMIN-010",
    "module": "admin",
    "name": "Version and about",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ADMIN-025"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SYNC-001",
    "module": "sync",
    "name": "Offline and sync queue status",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SYNC-001",
      "FOS-SYNC-002",
      "FOS-SYNC-003",
      "FOS-SYNC-004",
      "FOS-SYNC-005"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SYNC-002",
    "module": "sync",
    "name": "Conflict rejection and dead letter",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SYNC-006",
      "FOS-SYNC-007",
      "FOS-SYNC-008",
      "FOS-SYNC-009"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SYNC-003",
    "module": "sync",
    "name": "Authentication access and service outages",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SYNC-010",
      "FOS-SYNC-011",
      "FOS-SYNC-012",
      "FOS-SYNC-013"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SYNC-004",
    "module": "sync",
    "name": "Local safety and manual sync",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SYNC-014",
      "FOS-SYNC-015"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-SYNC-005",
    "module": "sync",
    "name": "Sync history reconciliation and trace",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-SYNC-016",
      "FOS-SYNC-017",
      "FOS-SYNC-018"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ATOM-001",
    "module": "atom",
    "name": "Core selectors",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ATOM-001",
      "FOS-ATOM-002",
      "FOS-ATOM-003",
      "FOS-ATOM-004",
      "FOS-ATOM-005",
      "FOS-ATOM-006",
      "FOS-ATOM-007",
      "FOS-ATOM-008",
      "FOS-ATOM-009",
      "FOS-ATOM-010",
      "FOS-ATOM-011",
      "FOS-ATOM-012",
      "FOS-ATOM-013",
      "FOS-ATOM-014"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ATOM-002",
    "module": "atom",
    "name": "Capture pickers and scanners",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ATOM-015",
      "FOS-ATOM-016",
      "FOS-ATOM-017",
      "FOS-ATOM-018",
      "FOS-ATOM-019",
      "FOS-ATOM-020"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ATOM-003",
    "module": "atom",
    "name": "Filtering sorting and bulk actions",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ATOM-021",
      "FOS-ATOM-022",
      "FOS-ATOM-023",
      "FOS-ATOM-024"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ATOM-004",
    "module": "atom",
    "name": "Confirmations conflict and permissions",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ATOM-025",
      "FOS-ATOM-026",
      "FOS-ATOM-027",
      "FOS-ATOM-028"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ATOM-005",
    "module": "atom",
    "name": "Export and share atoms",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ATOM-029",
      "FOS-ATOM-030"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  },
  {
    "feature_id": "FTR-ATOM-006",
    "module": "atom",
    "name": "Empty loading error and sync receipts",
    "mandatory": true,
    "completion_phase": 6,
    "screen_ids": [
      "FOS-ATOM-031",
      "FOS-ATOM-032",
      "FOS-ATOM-033",
      "FOS-ATOM-034",
      "FOS-ATOM-035"
    ],
    "contract_status": "OPEN",
    "feature_green": false
  }
]

_SCREEN_TO_FEATURE: dict[str, str] = {}
for _feature in FEATURES:
    for _screen_id in _feature["screen_ids"]:
        if _screen_id in _SCREEN_TO_FEATURE:
            raise RuntimeError(f"duplicate primary feature binding for {_screen_id}")
        _SCREEN_TO_FEATURE[_screen_id] = _feature["feature_id"]

def feature_ids_for_screen(screen_id: str) -> list[str]:
    feature_id = _SCREEN_TO_FEATURE.get(screen_id)
    return [feature_id] if feature_id else []

def catalog_features() -> list[dict]:
    return deepcopy(FEATURES)

def validate_screen_coverage(screen_ids: list[str]) -> None:
    if len(screen_ids) != len(set(screen_ids)):
        raise RuntimeError("screen registry contains duplicate Screen IDs")
    registry = set(screen_ids)
    mapped = set(_SCREEN_TO_FEATURE)
    missing = sorted(registry - mapped)
    extra = sorted(mapped - registry)
    if missing or extra:
        raise RuntimeError(f"feature catalogue coverage mismatch; missing={missing}, extra={extra}")
    if len(mapped) != 545:
        raise RuntimeError(f"feature catalogue must bind 545 screens, got {len(mapped)}")
