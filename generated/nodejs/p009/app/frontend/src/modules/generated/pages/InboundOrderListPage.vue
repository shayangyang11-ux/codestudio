<template>
  <section class="generated-runtime-page">
    <UiContractPreviewRuntime
      :contract="contract"
      display-mode="artboard"
      :project-theme-profile="projectThemeProfile"
      :navigate-request="handleNavigate"
      @prototype-navigate="handlePrototypeNavigate"
      @prototype-action="handlePrototypeAction"
    />
  </section>
</template>

<script setup>
import { useRouter } from 'vue-router'
import UiContractPreviewRuntime from '../../../shared/ui-runtime/UiContractPreviewRuntime.vue'

const contract = {
  "page": {
    "route": "/inbound/orders",
    "title": "入库单列表页",
    "layout": {
      "type": "TABLE_LIST",
      "columns": 1,
      "regions": [
        {
          "title": "页头",
          "regionId": "header",
          "componentIds": [
            "inbound_order_list_page_header"
          ]
        },
        {
          "title": "查询条件",
          "regionId": "filters",
          "componentIds": [
            "inbound_order_list_page_filter_section"
          ]
        },
        {
          "title": "数据列表",
          "regionId": "table",
          "componentIds": [
            "inbound_order_list_page_table_section"
          ]
        },
        {
          "title": "空状态",
          "regionId": "empty",
          "componentIds": [
            "inbound_order_list_page_empty_section"
          ]
        }
      ]
    },
    "pageId": "a44d763d-16e3-429f-9e14-870c2b8e496d",
    "pageKey": "inbound_order_list_page",
    "pageKind": "LIST_PAGE",
    "pageName": "入库单列表页",
    "description": "面向业务人员的入库单列表页查询、筛选和数据管理页面。",
    "componentTree": [
      {
        "tabs": [],
        "type": "PAGE_HEADER",
        "props": {
          "visualRole": "enterprisePageHeader",
          "pagePattern": "SEARCH_LIST",
          "rowActionIds": [],
          "elementPicker": {
            "textRoles": [
              "title",
              "description"
            ],
            "selectable": true
          },
          "elementStyles": {},
          "a9VisualQuality": "demoGrade"
        },
        "title": "入库单列表页",
        "fields": [],
        "columns": [],
        "modalId": null,
        "children": [],
        "actionIds": [
          "inbound_order_list_page_query",
          "inbound_order_list_page_create",
          "inbound_order_list_page_export"
        ],
        "featureIds": [],
        "componentId": "inbound_order_list_page_header",
        "description": "面向业务人员的入库单列表页查询、筛选和数据管理页面。",
        "dataBindingKey": null
      },
      {
        "tabs": [],
        "type": "SECTION",
        "props": {
          "layout": {
            "columns": 1
          },
          "fullWidth": true,
          "visualRole": "enterpriseFilterSection",
          "pagePattern": "SEARCH_LIST",
          "rowActionIds": [],
          "elementPicker": {
            "selectable": true
          },
          "elementStyles": {},
          "a9VisualQuality": "demoGrade"
        },
        "title": "查询条件",
        "fields": [],
        "columns": [],
        "modalId": null,
        "children": [
          {
            "tabs": [],
            "type": "FORM",
            "props": {
              "layout": {
                "gap": "16px 20px",
                "columns": 4
              },
              "visualRole": "enterpriseFilterForm",
              "pagePattern": "SEARCH_LIST",
              "rowActionIds": [],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "查询筛选",
            "fields": [
              {
                "type": "INPUT",
                "label": "入库单号",
                "options": [],
                "disabled": false,
                "fieldKey": "inboundno",
                "helpText": "",
                "required": false,
                "featureIds": [],
                "placeholder": "请输入入库单号",
                "defaultValue": null,
                "validationRuleIds": []
              },
              {
                "type": "SELECT",
                "label": "供应商",
                "options": [],
                "disabled": false,
                "fieldKey": "supplierid",
                "helpText": "",
                "required": false,
                "featureIds": [],
                "placeholder": "请选择供应商",
                "defaultValue": null,
                "validationRuleIds": []
              },
              {
                "type": "SELECT",
                "label": "仓库",
                "options": [],
                "disabled": false,
                "fieldKey": "warehouseid",
                "helpText": "",
                "required": false,
                "featureIds": [],
                "placeholder": "请选择仓库",
                "defaultValue": null,
                "validationRuleIds": []
              },
              {
                "type": "INPUT",
                "label": "关键字",
                "options": [],
                "disabled": false,
                "fieldKey": "keyword",
                "helpText": "",
                "required": false,
                "featureIds": [],
                "placeholder": "请输入关键字",
                "defaultValue": null,
                "validationRuleIds": []
              }
            ],
            "columns": [],
            "modalId": null,
            "children": [],
            "actionIds": [
              "inbound_order_list_page_query",
              "inbound_order_list_page_reset_filters"
            ],
            "featureIds": [],
            "componentId": "inbound_order_list_page_filters",
            "description": "根据业务字段快速定位数据。",
            "dataBindingKey": null
          }
        ],
        "actionIds": [],
        "featureIds": [],
        "componentId": "inbound_order_list_page_filter_section",
        "description": "全宽筛选区，不参与卡片宫格布局。",
        "dataBindingKey": null
      },
      {
        "tabs": [],
        "type": "SECTION",
        "props": {
          "layout": {
            "columns": 1
          },
          "fullWidth": true,
          "visualRole": "enterpriseTableSection",
          "pagePattern": "SEARCH_LIST",
          "rowActionIds": [],
          "elementPicker": {
            "selectable": true
          },
          "elementStyles": {},
          "a9VisualQuality": "demoGrade"
        },
        "title": "数据列表",
        "fields": [],
        "columns": [],
        "modalId": null,
        "children": [
          {
            "tabs": [],
            "type": "TOOLBAR",
            "props": {
              "align": "right",
              "placement": "tableHeader",
              "visualRole": "enterpriseTableToolbar",
              "pagePattern": "SEARCH_LIST",
              "rowActionIds": [],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "",
            "fields": [],
            "columns": [],
            "modalId": null,
            "children": [],
            "actionIds": [
              "inbound_order_list_page_create",
              "inbound_order_list_page_export"
            ],
            "featureIds": [],
            "componentId": "inbound_order_list_page_table_toolbar",
            "description": "",
            "dataBindingKey": null
          },
          {
            "tabs": [],
            "type": "TABLE",
            "props": {
              "density": "compact",
              "fullWidth": true,
              "pagination": true,
              "visualRole": "enterpriseDataTable",
              "pagePattern": "SEARCH_LIST",
              "rowActionIds": [
                "inbound_order_list_page_view_detail",
                "inbound_order_list_page_delete_row"
              ],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "数据列表",
            "fields": [],
            "columns": [
              {
                "type": "TEXT",
                "label": "入库单号",
                "width": 140,
                "fieldKey": "inboundno",
                "sortable": true,
                "columnKey": "inboundno"
              },
              {
                "type": "TEXT",
                "label": "供应商",
                "width": 160,
                "fieldKey": "supplierid",
                "sortable": true,
                "columnKey": "supplierid"
              },
              {
                "type": "TEXT",
                "label": "仓库",
                "width": 180,
                "fieldKey": "warehouseid",
                "sortable": true,
                "columnKey": "warehouseid"
              },
              {
                "type": "TAG",
                "label": "入库状态",
                "width": 200,
                "fieldKey": "status",
                "sortable": false,
                "columnKey": "status"
              },
              {
                "type": "TEXT",
                "label": "经办人",
                "width": 200,
                "fieldKey": "operatorid",
                "sortable": true,
                "columnKey": "operatorid"
              },
              {
                "type": "DATE",
                "label": "创建时间",
                "width": 200,
                "fieldKey": "createdat",
                "sortable": true,
                "columnKey": "createdat"
              },
              {
                "type": "NUMBER",
                "label": "入库数量",
                "width": 200,
                "fieldKey": "inboundqty",
                "sortable": true,
                "columnKey": "inboundqty"
              },
              {
                "type": "ACTIONS",
                "label": "操作",
                "width": 160,
                "fieldKey": "actions",
                "sortable": false,
                "columnKey": "actions"
              }
            ],
            "modalId": null,
            "children": [],
            "actionIds": [
              "inbound_order_list_page_query"
            ],
            "featureIds": [],
            "componentId": "inbound_order_list_page_data_table",
            "description": "展示筛选后的业务数据，支持分页与行级操作。",
            "dataBindingKey": "tableRows"
          }
        ],
        "actionIds": [],
        "featureIds": [],
        "componentId": "inbound_order_list_page_table_section",
        "description": "表格占满内容区，工具栏和表格保持同一业务区域。",
        "dataBindingKey": null
      },
      {
        "tabs": [],
        "type": "SECTION",
        "props": {
          "layout": {
            "columns": 1
          },
          "fullWidth": true,
          "visualRole": "enterpriseEmptySection",
          "pagePattern": "SEARCH_LIST",
          "rowActionIds": [],
          "elementPicker": {
            "selectable": true
          },
          "elementStyles": {},
          "a9VisualQuality": "demoGrade"
        },
        "title": "",
        "fields": [],
        "columns": [],
        "modalId": null,
        "children": [
          {
            "tabs": [],
            "type": "EMPTY_STATE",
            "props": {
              "visualRole": "enterpriseTableEmptyState",
              "pagePattern": "SEARCH_LIST",
              "rowActionIds": [],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "暂无数据",
            "fields": [],
            "columns": [],
            "modalId": null,
            "children": [],
            "actionIds": [],
            "featureIds": [],
            "componentId": "inbound_order_list_page_empty_state",
            "description": "调整筛选条件后重新查询。",
            "dataBindingKey": null
          }
        ],
        "actionIds": [],
        "featureIds": [],
        "componentId": "inbound_order_list_page_empty_section",
        "description": "",
        "dataBindingKey": null
      }
    ]
  },
  "pages": [],
  "module": {},
  "routes": [],
  "identity": {
    "pageId": "a44d763d-16e3-429f-9e14-870c2b8e496d",
    "pageKey": "inbound_order_list_page",
    "pageKind": "LIST_PAGE",
    "pageName": "入库单列表页",
    "projectId": "c53e6450-d3ba-4d0c-86b0-f45a0689ca33"
  },
  "mockData": {
    "records": [
      {
        "id": "mock_001",
        "code": "MOCK-001",
        "name": "示例记录",
        "owner": "系统用户",
        "status": "待处理",
        "createdAt": "2026-01-01 09:00"
      }
    ],
    "pageMeta": {
      "pageName": "入库单列表页",
      "generatedFor": "UI_CONTRACT_MOCK_PREVIEW"
    },
    "tableRows": [
      {
        "status": "待处理",
        "createdat": "2026-08-01",
        "inboundno": "入库单号示例1",
        "inboundqty": 10,
        "operatorid": "经办人示例1",
        "supplierid": "供应商示例1",
        "warehouseid": "仓库示例1"
      },
      {
        "status": "已完成",
        "createdat": "2026-08-02",
        "inboundno": "入库单号示例2",
        "inboundqty": 20,
        "operatorid": "经办人示例2",
        "supplierid": "供应商示例2",
        "warehouseid": "仓库示例2"
      }
    ],
    "featureCoveragePreview": [
      {
        "status": "已覆盖",
        "featureId": "e4c9a770-ba13-4a0e-bc85-3c82694ee76c",
        "featureName": "入库单查询"
      }
    ]
  },
  "pageStates": [
    {
      "type": "DEFAULT",
      "title": "默认态",
      "message": "展示查询筛选区、工具栏和数据表格。",
      "stateKey": "DEFAULT",
      "visibleComponentIds": [
        "inbound_order_list_page_header",
        "inbound_order_list_page_filter_section",
        "inbound_order_list_page_filters",
        "inbound_order_list_page_table_section",
        "inbound_order_list_page_table_toolbar",
        "inbound_order_list_page_data_table"
      ],
      "disabledComponentIds": []
    },
    {
      "type": "LOADING",
      "title": "加载中",
      "message": "正在加载列表数据。",
      "stateKey": "LOADING",
      "visibleComponentIds": [
        "inbound_order_list_page_header",
        "inbound_order_list_page_filter_section",
        "inbound_order_list_page_filters",
        "inbound_order_list_page_table_section",
        "inbound_order_list_page_table_toolbar",
        "inbound_order_list_page_data_table"
      ],
      "disabledComponentIds": []
    },
    {
      "type": "ERROR",
      "title": "错误态",
      "message": "查询接口异常或权限不足。",
      "stateKey": "ERROR",
      "visibleComponentIds": [
        "inbound_order_list_page_header",
        "inbound_order_list_page_filter_section",
        "inbound_order_list_page_filters",
        "inbound_order_list_page_table_section",
        "inbound_order_list_page_table_toolbar",
        "inbound_order_list_page_data_table"
      ],
      "disabledComponentIds": []
    },
    {
      "type": "EMPTY",
      "title": "空状态",
      "message": "筛选结果为空时展示。",
      "stateKey": "EMPTY",
      "visibleComponentIds": [
        "inbound_order_list_page_header",
        "inbound_order_list_page_filter_section",
        "inbound_order_list_page_filters",
        "inbound_order_list_page_empty_section",
        "inbound_order_list_page_empty_state"
      ],
      "disabledComponentIds": []
    },
    {
      "type": "SUCCESS",
      "title": "操作成功",
      "message": "关键操作完成后展示成功反馈。",
      "stateKey": "SUCCESS",
      "visibleComponentIds": [],
      "disabledComponentIds": []
    }
  ],
  "apiBindings": [
    {
      "path": "/api/mock/inbound_order_list_page/list",
      "method": "GET",
      "status": "MOCK",
      "bindingKey": "tableRows",
      "requestSchema": {},
      "responseSchema": {},
      "usedByActionIds": [
        "inbound_order_list_page_query",
        "inbound_order_list_page_export"
      ]
    },
    {
      "path": "/api/inbound_order_list_page/inbound-order-query",
      "method": "POST",
      "status": "PLANNED",
      "bindingKey": "inbound_order_query_action",
      "requestSchema": {},
      "responseSchema": {},
      "usedByActionIds": [
        "inbound_order_query_action"
      ]
    },
    {
      "path": "/api/mock/inbound_order_list_page_export",
      "method": "GET",
      "status": "MOCK",
      "bindingKey": "inbound_order_list_page_export_api",
      "requestSchema": {},
      "responseSchema": {},
      "usedByActionIds": [
        "inbound_order_list_page_export"
      ]
    }
  ],
  "assumptions": [
    "供应商、仓库选项为静态示例数据，后续可替换为接口数据。",
    "创建时间筛选使用日期时间范围选择器。",
    "表格分页功能由运行时根据 mockData 自动处理。",
    "A9.7: generated prototype is hardened as a demo-grade, canvas-selectable, AI-editable UI contract."
  ],
  "artifactType": "交互原型",
  "interactions": [
    {
      "type": "QUERY_DATA",
      "label": "查询",
      "payload": {},
      "actionId": "inbound_order_list_page_query",
      "targetId": "inbound_order_list_page_filters",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": "tableRows",
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_filters",
      "confirmationMessage": ""
    },
    {
      "type": "RESET_FORM",
      "label": "重置",
      "payload": {},
      "actionId": "inbound_order_list_page_reset_filters",
      "targetId": "inbound_order_list_page_filters",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": null,
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_filters",
      "confirmationMessage": ""
    },
    {
      "type": "SHOW_TOAST",
      "label": "新增",
      "payload": {},
      "actionId": "inbound_order_list_page_create",
      "targetId": "inbound_order_list_page_filters",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": null,
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_filters",
      "confirmationMessage": ""
    },
    {
      "type": "QUERY_DATA",
      "label": "导出",
      "payload": {},
      "actionId": "inbound_order_list_page_export",
      "targetId": "inbound_order_list_page_filters",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": "inbound_order_list_page_export_api",
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_filters",
      "confirmationMessage": ""
    },
    {
      "type": "DELETE_ROW",
      "label": "删除",
      "payload": {},
      "actionId": "inbound_order_list_page_delete_row",
      "targetId": "inbound_order_list_page_filters",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": null,
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_filters",
      "confirmationMessage": ""
    },
    {
      "type": "SHOW_TOAST",
      "label": "查看",
      "payload": {
        "source": "A9_7_REAL_GENERATION_HARDENER"
      },
      "actionId": "inbound_order_list_page_view_detail",
      "targetId": "inbound_order_list_page_data_table",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": null,
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_data_table",
      "confirmationMessage": ""
    },
    {
      "type": "SUBMIT_FORM",
      "label": "入库单查询",
      "payload": {},
      "actionId": "inbound_order_query_action",
      "targetId": "inbound_order_list_page_filters",
      "featureIds": [],
      "scenarioKey": null,
      "targetPageId": null,
      "targetTabKey": null,
      "dataBindingKey": "inbound_order_query_action",
      "failureActionIds": [],
      "successActionIds": [],
      "sourceComponentId": "inbound_order_list_page_table_toolbar",
      "confirmationMessage": ""
    }
  ],
  "mockScenarios": [
    {
      "delayMs": 300,
      "message": "查询成功。",
      "outcome": "SUCCESS",
      "actionId": "inbound_order_list_page_query",
      "response": {
        "message": "操作成功。",
        "success": true
      },
      "scenarioKey": "inbound_order_list_page_query_success",
      "nextPageState": "DEFAULT"
    },
    {
      "delayMs": 300,
      "message": "查询失败，请稍后重试。",
      "outcome": "FAILURE",
      "actionId": "inbound_order_list_page_query",
      "response": {
        "message": "操作失败，请稍后重试。",
        "success": false
      },
      "scenarioKey": "inbound_order_list_page_query_failure",
      "nextPageState": "ERROR"
    },
    {
      "delayMs": 300,
      "message": "重置成功。",
      "outcome": "SUCCESS",
      "actionId": "inbound_order_list_page_reset_filters",
      "response": {
        "message": "操作成功。",
        "success": true
      },
      "scenarioKey": "inbound_order_list_page_reset_filters_success",
      "nextPageState": "DEFAULT"
    },
    {
      "delayMs": 300,
      "message": "重置失败，请稍后重试。",
      "outcome": "FAILURE",
      "actionId": "inbound_order_list_page_reset_filters",
      "response": {
        "message": "操作失败，请稍后重试。",
        "success": false
      },
      "scenarioKey": "inbound_order_list_page_reset_filters_failure",
      "nextPageState": "ERROR"
    },
    {
      "delayMs": 300,
      "message": "新增成功。",
      "outcome": "SUCCESS",
      "actionId": "inbound_order_list_page_create",
      "response": {
        "message": "操作成功。",
        "success": true
      },
      "scenarioKey": "inbound_order_list_page_create_success",
      "nextPageState": "DEFAULT"
    },
    {
      "delayMs": 300,
      "message": "新增失败，请稍后重试。",
      "outcome": "FAILURE",
      "actionId": "inbound_order_list_page_create",
      "response": {
        "message": "操作失败，请稍后重试。",
        "success": false
      },
      "scenarioKey": "inbound_order_list_page_create_failure",
      "nextPageState": "ERROR"
    },
    {
      "delayMs": 300,
      "message": "导出成功。",
      "outcome": "SUCCESS",
      "actionId": "inbound_order_list_page_export",
      "response": {
        "message": "操作成功。",
        "success": true
      },
      "scenarioKey": "inbound_order_list_page_export_success",
      "nextPageState": "DEFAULT"
    },
    {
      "delayMs": 300,
      "message": "导出失败，请稍后重试。",
      "outcome": "FAILURE",
      "actionId": "inbound_order_list_page_export",
      "response": {
        "message": "操作失败，请稍后重试。",
        "success": false
      },
      "scenarioKey": "inbound_order_list_page_export_failure",
      "nextPageState": "ERROR"
    },
    {
      "delayMs": 300,
      "message": "删除成功。",
      "outcome": "SUCCESS",
      "actionId": "inbound_order_list_page_delete_row",
      "response": {
        "message": "操作成功。",
        "success": true
      },
      "scenarioKey": "inbound_order_list_page_delete_row_success",
      "nextPageState": "DEFAULT"
    },
    {
      "delayMs": 300,
      "message": "删除失败，请稍后重试。",
      "outcome": "FAILURE",
      "actionId": "inbound_order_list_page_delete_row",
      "response": {
        "message": "操作失败，请稍后重试。",
        "success": false
      },
      "scenarioKey": "inbound_order_list_page_delete_row_failure",
      "nextPageState": "ERROR"
    }
  ],
  "schemaVersion": "1.0",
  "qualitySummary": {
    "score": 95,
    "blockers": [],
    "warnings": [
      "已执行 UI_CONTRACT 引用闭环：FEATURE、组件、动作、Mock 场景、API 绑定均已归一化到当前 PAGE。",
      "已通过 UI_CONTRACT 最终引用守门器：组件、字段、动作、动作链、Mock 场景、API 绑定、页面状态、FEATURE 覆盖均已闭环。",
      "A9.7 demo-grade hardener applied: page header, selectable elements, visual metadata, actions, and page states normalized."
    ],
    "coverageRate": 100,
    "generationMode": "LLM_STRUCTURED"
  },
  "analysisSummary": "已按 A9.9.7 页面类型规则为“入库单列表页”生成 SEARCH_LIST 语义骨架，页面结构、组件、字段、动作、状态与 Mock 数据已统一闭环。 已补强 featureCoverage、pageStates、mockScenarios、apiBindings、mockData、developmentRules 和 Development Agent 交接摘要；当前 FEATURE 覆盖率 100%，功能覆盖项 1 个。",
  "featureCoverage": [
    {
      "notes": [
        "已由 UI_CONTRACT 最终引用守门器归一化为当前 PAGE 真实引用。"
      ],
      "status": "FULL",
      "actionIds": [
        "inbound_order_list_page_query",
        "inbound_order_list_page_reset_filters",
        "inbound_order_list_page_create",
        "inbound_order_list_page_export",
        "inbound_order_list_page_delete_row"
      ],
      "featureId": "e4c9a770-ba13-4a0e-bc85-3c82694ee76c",
      "featureKey": "inbound_order_query",
      "featureName": "入库单查询",
      "componentIds": [
        "inbound_order_list_page_header",
        "inbound_order_list_page_filters",
        "inbound_order_list_page_table_toolbar"
      ]
    }
  ],
  "validationRules": [],
  "developmentRules": {
    "mustFollowFields": true,
    "mustFollowLayout": true,
    "mustFollowApiBindings": true,
    "allowStyleOptimization": true,
    "arbitraryScriptForbidden": true,
    "mockMustUseScenarioEngine": true,
    "mustFollowInteractionFlow": true,
    "mustFollowValidationRules": true,
    "breakingChangeRequiresReview": true
  },
  "navigationTargets": [
    {
      "route": "仓储管理域/入库管理/入库单管理/入库单创建/入库单创建页",
      "status": "RESOLVED",
      "targetPageId": "51d24519-5f5c-4518-8a12-0974d7bca9eb",
      "targetPageKey": "inbound_order_create_page",
      "targetPageName": "入库单创建页"
    },
    {
      "route": "仓储管理域/入库管理/入库单管理/入库单明细查看/入库单详情页",
      "status": "RESOLVED",
      "targetPageId": "d2dfd21d-b616-4c48-8464-5865a65b57ce",
      "targetPageKey": "inbound_order_detail_page",
      "targetPageName": "入库单详情页"
    },
    {
      "route": "仓储管理域/报表分析/入库报表/入库统计/入库统计看板",
      "status": "RESOLVED",
      "targetPageId": "94ca6f70-7eb7-4674-9384-fab7ca02cde0",
      "targetPageKey": "inbound_statistics_dashboard",
      "targetPageName": "入库统计看板"
    },
    {
      "route": "仓储管理域/出库管理/出库单管理/出库单查询/出库单列表页",
      "status": "RESOLVED",
      "targetPageId": "4a6f70c5-8584-4886-b121-0ceaa9cbc1ee",
      "targetPageKey": "outbound_order_list_page",
      "targetPageName": "出库单列表页"
    },
    {
      "route": "仓储管理域/系统管理/用户管理/用户编辑/用户编辑页",
      "status": "RESOLVED",
      "targetPageId": "295ebf6d-9233-4f99-bd84-831175799038",
      "targetPageKey": "user_form_page",
      "targetPageName": "用户编辑页"
    },
    {
      "route": "仓储管理域/系统管理/角色权限管理/角色列表/角色列表页",
      "status": "RESOLVED",
      "targetPageId": "342741f6-af6d-4f15-9f92-e85dd9cefec1",
      "targetPageKey": "role_list_page",
      "targetPageName": "角色列表页"
    },
    {
      "route": "仓储管理域/系统管理/角色权限管理/角色编辑/角色编辑页",
      "status": "RESOLVED",
      "targetPageId": "298a3fd4-371d-461f-bc08-f43192d3f4fe",
      "targetPageKey": "role_form_page",
      "targetPageName": "角色编辑页"
    },
    {
      "route": "仓储管理域/库存管理/库存盘点/盘点结果录入/盘点结果录入页",
      "status": "RESOLVED",
      "targetPageId": "0c2b97b1-8e8f-4825-b910-678f2d300264",
      "targetPageKey": "stocktake_result_page",
      "targetPageName": "盘点结果录入页"
    },
    {
      "route": "仓储管理域/库存管理/库存盘点/盘点单创建/盘点单创建页",
      "status": "RESOLVED",
      "targetPageId": "8500bb9f-a622-4b32-999a-64fd0d5831b8",
      "targetPageKey": "stocktake_create_page",
      "targetPageName": "盘点单创建页"
    },
    {
      "route": "仓储管理域/出库管理/出库单管理/出库单创建/出库单创建页",
      "status": "RESOLVED",
      "targetPageId": "1ee4756d-d43f-46bd-bdf3-fbfc49075103",
      "targetPageKey": "outbound_order_create_page",
      "targetPageName": "出库单创建页"
    },
    {
      "route": "仓储管理域/系统管理/用户管理/用户列表/用户列表页",
      "status": "RESOLVED",
      "targetPageId": "de8e6b1f-1c41-4b61-aeba-a5e4438afbcc",
      "targetPageKey": "user_list_page",
      "targetPageName": "用户列表页"
    },
    {
      "route": "仓储管理域/库存管理/库存查询/库存列表/库存列表页",
      "status": "RESOLVED",
      "targetPageId": "27cefa5c-63bd-4b32-9649-309cf3139ebe",
      "targetPageKey": "inventory_list_page",
      "targetPageName": "库存列表页"
    },
    {
      "route": "仓储管理域/入库管理/入库单管理/入库单审核提交/入库单审核页",
      "status": "RESOLVED",
      "targetPageId": "462484b6-54bf-41bb-a7ed-3cc9b2b74c19",
      "targetPageKey": "inbound_order_approval_page",
      "targetPageName": "入库单审核页"
    },
    {
      "route": "仓储管理域/报表分析/库存报表/库存统计/库存统计看板",
      "status": "RESOLVED",
      "targetPageId": "162328ed-0b8c-4e75-a664-8f6ae8de5aff",
      "targetPageKey": "inventory_statistics_dashboard",
      "targetPageName": "库存统计看板"
    },
    {
      "route": "仓储管理域/基础资料管理/供应商管理/供应商列表/供应商列表页",
      "status": "RESOLVED",
      "targetPageId": "2d4be8cc-5e8c-4135-8dcf-a85fcb3a84ca",
      "targetPageKey": "supplier_list_page",
      "targetPageName": "供应商列表页"
    },
    {
      "route": "仓储管理域/基础资料管理/供应商管理/供应商编辑/供应商编辑页",
      "status": "RESOLVED",
      "targetPageId": "4d06599e-a8ca-4980-9412-cdab5ebd7b3c",
      "targetPageKey": "supplier_form_page",
      "targetPageName": "供应商编辑页"
    },
    {
      "route": "仓储管理域/入库管理/入库单管理/入库单数量编辑/入库单数量编辑页",
      "status": "RESOLVED",
      "targetPageId": "a98b4600-8d28-4b02-8b33-1cd20d3931fe",
      "targetPageKey": "inbound_order_quantity_edit_page",
      "targetPageName": "入库单数量编辑页"
    },
    {
      "route": "仓储管理域/基础资料管理/仓库管理/仓库列表/仓库列表页",
      "status": "RESOLVED",
      "targetPageId": "af6c5e96-90a9-4280-a170-9590a30b32bb",
      "targetPageKey": "warehouse_list_page",
      "targetPageName": "仓库列表页"
    },
    {
      "route": "仓储管理域/基础资料管理/仓库管理/仓库编辑/仓库编辑页",
      "status": "RESOLVED",
      "targetPageId": "b7f4bb4f-d66a-4717-b7cc-07e2313fefaf",
      "targetPageKey": "warehouse_form_page",
      "targetPageName": "仓库编辑页"
    }
  ]
}
const projectThemeProfile = null
const navigationMap = {
  "inbound_order_list_page_create": "/inbound/orders/create",
  "inbound_order_list_page_view_detail": "/inbound/orders/:id"
}
const router = useRouter()

async function handleNavigate(payload = {}) {
  const target = resolveNavigationTarget(payload)
  if (target) {
    await router.push(target)
    return {
      ok: true,
      code: 'SUCCESS',
      message: '已打开目标页面。',
      route: target,
    }
  }

  return {
    ok: false,
    code: 'TARGET_NOT_FOUND',
    message: '当前动作没有配置目标页面。',
  }
}

function handlePrototypeNavigate(payload = {}) {
  handleNavigate(payload)
}

function handlePrototypeAction(payload = {}) {
  const target = resolveNavigationTarget(payload)
  if (target) router.push(target)
}

function resolveNavigationTarget(payload = {}) {
  const actionId = String(payload.actionId || payload.id || '').trim()
  const label = String(payload.label || payload.actionLabel || payload.title || '').trim()
  const actionType = String(payload.actionType || payload.type || '').trim()
  let target = navigationMap[actionId]

  if (!target) {
    target = navigationMap[semanticKey(actionId, label, actionType)]
  }

  if (!target && payload.route) {
    target = String(payload.route || '')
  }

  return normalizeRoute(target)
}

function semanticKey(...values) {
  const raw = values.filter(Boolean).join(' ').toLowerCase()
  if (!raw) return ''
  if (/返回|back|list/.test(raw)) return 'back_to_list'
  if (/详情|查看|detail|view/.test(raw)) return 'view_detail'
  if (/审核|审批|approve|approval|audit/.test(raw)) return 'approval'
  if (/创建|新建|新增|录入|create|new|add/.test(raw)) return 'create'
  if (/编辑|修改|edit|modify/.test(raw)) return 'edit'
  if (/删除|delete|remove/.test(raw)) return 'delete'
  return raw.replace(/[^a-z0-9一-鿿]+/g, '_').replace(/^_+|_+$/g, '')
}

function normalizeRoute(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  return '/' + text.replace(/^\/+|\/+$/g, '')
}
</script>

<style scoped>
.generated-runtime-page {
  min-width: 0;
}

.generated-runtime-page :deep(.runtime-shell) {
  min-height: auto;
}
</style>
