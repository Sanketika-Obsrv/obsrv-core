package org.sunbird.obsrv.summarizer.fixture

object EventFixtures {
  val timeIndex = Map(
    0 -> 3000,
    1 -> 1000,
    2 -> 1000,
    3 -> 2000,
    4 -> 1000,
    5 -> 3000,
    6 -> 2000,
    7 -> 1000,
    8 -> 1000,
  )

  // Valid Flow
  val CASE_1_START = """{"eid":"START","ets":1704067200000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"19897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_1_INTERACT = """{"eid":"INTERACT","ets":1704067201000,"ver":"3.0","mid":"INTERACT:f2462504e78e24c7b94537d99c890531","actor":{"id":"024d892b-cb89-4724-8545-7ba33612a084","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"19897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"web/en/page/embed-encharta"},"tags":["Infosys Ltd"],"edata":{"type":"chatbot","subtype":"mute-auto","object":{},"pageid":"web/en/page/embed-encharta","target":{"page":{"ip":"","pageid":"web/en/page/embed-encharta","pageUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","pageUrlParts":["web","en","page","embed-encharta"],"refferUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","httpRefferUrl":"","publicRef":""}}},"syncts":1701439288379,"@timestamp":"2023-12-01T14:01:28.379Z","flags":{"ex_processed":true}}"""
  val CASE_1_AUDIT = """{"eid":"AUDIT","ets":1704067202000,"ver":"3.0","mid":"AUDIT:39f3d2a9776c2843ba2d81daef97ec68","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"19897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"type":"Created","props":"Login","data":{}},"syncts":1701445708088,"@timestamp":"2023-12-01T15:48:28.088Z","flags":{"ex_processed":true}}"""
  val CASE_1_END = """{"eid":"END","ets":1704067203000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"19897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_1_TIME = 3000

  // Missing START, should assume START at INTERACT
  val CASE_2_INTERACT = """{"eid":"INTERACT","ets":1704067204000,"ver":"3.0","mid":"INTERACT:f2462504e78e24c7b94537d99c890531","actor":{"id":"024d892b-cb89-4724-8545-7ba33612a084","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"29897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"web/en/page/embed-encharta"},"tags":["Infosys Ltd"],"edata":{"type":"chatbot","subtype":"mute-auto","object":{},"pageid":"web/en/page/embed-encharta","target":{"page":{"ip":"","pageid":"web/en/page/embed-encharta","pageUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","pageUrlParts":["web","en","page","embed-encharta"],"refferUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","httpRefferUrl":"","publicRef":""}}},"syncts":1701439288379,"@timestamp":"2023-12-01T14:01:28.379Z","flags":{"ex_processed":true}}"""
  val CASE_2_END = """{"eid":"END","ets":1704067205000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"29897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_2_TIME = 1000

  // Missing END, should create END event at START ets + session break time or prev event time?
  val CASE_3_START = """{"eid":"START","ets":1704067228000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"39897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_3_INTERACT = """{"eid":"INTERACT","ets":1704067229000,"ver":"3.0","mid":"INTERACT:f2462504e78e24c7b94537d99c890531","actor":{"id":"024d892b-cb89-4724-8545-7ba33612a084","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"39897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"web/en/page/embed-encharta"},"tags":["Infosys Ltd"],"edata":{"type":"chatbot","subtype":"mute-auto","object":{},"pageid":"web/en/page/embed-encharta","target":{"page":{"ip":"","pageid":"web/en/page/embed-encharta","pageUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","pageUrlParts":["web","en","page","embed-encharta"],"refferUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","httpRefferUrl":"","publicRef":""}}},"syncts":1701439288379,"@timestamp":"2023-12-01T14:01:28.379Z","flags":{"ex_processed":true}}"""
  val CASE_3_TIME = 1000

  // Should skip idle time from session time; update prevEts ?
  val CASE_4_START = """{"eid":"START","ets":1704067206000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"49897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_4_IMPRESSION = """{"eid":"IMPRESSION","ets":1704067209000,"ver":"3.0","mid":"IMPRESSION:af7674ad5d19fe12bc1a75f8647d2d3e","actor":{"id":"bbdcc67e-74c1-4e93-832e-f584d6418d2e","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"49897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Infosys Ltd"],"edata":{"ip":"","pageid":"web/en/app/schedulo/events/edit","pageUrl":"web/en/app/schedulo/events/edit?mode\u003dedit\u0026eventId\u003dd488fc0b-b237-43f2-91d3-97d32edac0db","pageUrlParts":["web","en","app","schedulo","events","edit"],"refferUrl":"web/en/app/schedulo/events/event-details/d488fc0b-b237-43f2-91d3-97d32edac0db","httpRefferUrl":"","publicRef":""},"syncts":1701438490488,"@timestamp":"2023-12-01T13:48:10.488Z","flags":{"ex_processed":true}}"""
  val CASE_4_END = """{"eid":"END","ets":1704067210000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"49897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_4_TIME = 1000

  // should close first start on second start and create a 2nd session
  val CASE_5_START = """{"eid":"START","ets":1704067211000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"59897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_5_INTERACT = """{"eid":"INTERACT","ets":1704067212000,"ver":"3.0","mid":"INTERACT:f2462504e78e24c7b94537d99c890531","actor":{"id":"024d892b-cb89-4724-8545-7ba33612a084","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"59897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"web/en/page/embed-encharta"},"tags":["Infosys Ltd"],"edata":{"type":"chatbot","subtype":"mute-auto","object":{},"pageid":"web/en/page/embed-encharta","target":{"page":{"ip":"","pageid":"web/en/page/embed-encharta","pageUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","pageUrlParts":["web","en","page","embed-encharta"],"refferUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","httpRefferUrl":"","publicRef":""}}},"syncts":1701439288379,"@timestamp":"2023-12-01T14:01:28.379Z","flags":{"ex_processed":true}}"""
  val CASE_5_SECOND_START = """{"eid":"START","ets":1704067213000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"59897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_5_END = """{"eid":"END","ets":1704067214000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"59897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_5_TIME = 2000
  val CASE_5_TIME_2 = 1000

  // should not create an event
  val CASE_6_END = """{"eid":"END","ets":1704067215000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"69897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""

  // should skip other edata type and mode START events
  val CASE_7_START = """{"eid":"START","ets":1704067216000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"79897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_7_INTERACT = """{"eid":"INTERACT","ets":1704067217000,"ver":"3.0","mid":"INTERACT:f2462504e78e24c7b94537d99c890531","actor":{"id":"024d892b-cb89-4724-8545-7ba33612a084","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"79897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"web/en/page/embed-encharta"},"tags":["Infosys Ltd"],"edata":{"type":"chatbot","subtype":"mute-auto","object":{},"pageid":"web/en/page/embed-encharta","target":{"page":{"ip":"","pageid":"web/en/page/embed-encharta","pageUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","pageUrlParts":["web","en","page","embed-encharta"],"refferUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","httpRefferUrl":"","publicRef":""}}},"syncts":1701439288379,"@timestamp":"2023-12-01T14:01:28.379Z","flags":{"ex_processed":true}}"""
  val CASE_7_PAGE_START = """{"eid":"START","ets":1704067218000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"79897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"web/nl/page/home","type":"Page","mode":"View","stageid":"","duration":1.701445708959E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_7_END = """{"eid":"END","ets":1704067219000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"79897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_7_TIME = 3000

  // watermark should sort events in correct order - valid case
  val CASE_8_INTERACT = """{"eid":"INTERACT","ets":1704067221000,"ver":"3.0","mid":"INTERACT:f2462504e78e24c7b94537d99c890531","actor":{"id":"024d892b-cb89-4724-8545-7ba33612a084","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"89897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"web/en/page/embed-encharta"},"tags":["Infosys Ltd"],"edata":{"type":"chatbot","subtype":"mute-auto","object":{},"pageid":"web/en/page/embed-encharta","target":{"page":{"ip":"","pageid":"web/en/page/embed-encharta","pageUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","pageUrlParts":["web","en","page","embed-encharta"],"refferUrl":"web/en/page/embed-encharta?content\u003dd769aa32-f532-7678-71db-c9a85c1b076d","httpRefferUrl":"","publicRef":""}}},"syncts":1701439288379,"@timestamp":"2023-12-01T14:01:28.379Z","flags":{"ex_processed":true}}"""
  val CASE_8_START = """{"eid":"START","ets":1704067220000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"89897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_8_END = """{"eid":"END","ets":1704067222000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"89897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_8_TIME = 2000

  // watermark should sort events in correct order - should create 2 events
  val CASE_9_IMPRESSION = """{"eid":"IMPRESSION","ets":1704067226000,"ver":"3.0","mid":"IMPRESSION:af7674ad5d19fe12bc1a75f8647d2d3e","actor":{"id":"bbdcc67e-74c1-4e93-832e-f584d6418d2e","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36","ver":"1.0.0"},"env":"dev","sid":"","did":"99897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Infosys Ltd"],"edata":{"ip":"","pageid":"web/en/app/schedulo/events/edit","pageUrl":"web/en/app/schedulo/events/edit?mode\u003dedit\u0026eventId\u003dd488fc0b-b237-43f2-91d3-97d32edac0db","pageUrlParts":["web","en","app","schedulo","events","edit"],"refferUrl":"web/en/app/schedulo/events/event-details/d488fc0b-b237-43f2-91d3-97d32edac0db","httpRefferUrl":"","publicRef":""},"syncts":1701438490488,"@timestamp":"2023-12-01T13:48:10.488Z","flags":{"ex_processed":true}}"""
  val CASE_9_START = """{"eid":"START","ets":1704067223000,"ver":"3.0","mid":"START:4d101b5f59c0305de375974c7cc1a34c","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"99897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0.0"},"tags":["Sitrain"],"edata":{"id":"","type":"app","mode":"view","stageid":"","duration":1.701439278237E9},"syncts":1701439286495,"@timestamp":"2023-12-01T14:01:26.495Z","flags":{"ex_processed":true}}"""
  val CASE_9_END = """{"eid":"END","ets":1704067227000,"ver":"3.0","mid":"END:802a40580dfda5048d79cbac6ecebfc5","actor":{"id":"0382b67c-fcf2-467f-8017-37b7ac5e90ab","type":"User"},"context":{"channel":"Sitrain","pdata":{"id":"sitrain-dev-web-ui-2.0","pid":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0","ver":"1.0.0"},"env":"dev","sid":"","did":"99897c17671e2b3305ef891e5b62676e","cdata":[],"rollup":{}},"object":{"ver":"1.0","id":"page/home"},"tags":["Sitrain"],"edata":{"type":"app","mode":"view","contentId":"","duration":24.054000000000002},"syncts":1701443523157,"@timestamp":"2023-12-01T15:12:03.157Z","flags":{"ex_processed":true}}"""
  val CASE_9_TIME = 1000

}