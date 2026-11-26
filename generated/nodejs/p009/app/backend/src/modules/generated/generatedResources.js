export const generatedResources = [
  {
    "resourceKey": "inbound_order_create_page",
    "apiSegment": "inbound-orders",
    "title": "入库单创建页",
    "pageKey": "inbound_order_create_page",
    "routePath": "/inbound/orders/create",
    "description": "录入供应商、仓库、经办人等基本信息，并添加入库商品明细，保存或提交入库单。",
    "pageKind": "form_page",
    "surfaceType": "SECONDARY_ROUTE",
    "navigationVisible": false,
    "entryMode": "ACTION_ROUTE",
    "uiContract": {
      "page": {
        "name": "新建入库",
        "route": "/inbound/orders/create",
        "title": "新建入库",
        "layout": {
          "type": "FORM_DETAIL",
          "columns": 1,
          "regions": [
            {
              "title": "页头",
              "regionId": "header",
              "componentIds": [
                "inbound_order_create_page_header",
                "inbound_order_create_page_page_actions"
              ]
            },
            {
              "title": "基本信息",
              "regionId": "basic",
              "componentIds": [
                "inbound_order_create_page_basic_section"
              ]
            },
            {
              "title": "明细信息",
              "regionId": "details",
              "componentIds": [
                "inbound_order_create_page_detail_section"
              ]
            },
            {
              "title": "操作区",
              "regionId": "footer",
              "componentIds": [
                "inbound_order_create_page_business_hint",
                "inbound_order_create_page_bottom_actions"
              ]
            }
          ]
        },
        "pageId": "51d24519-5f5c-4518-8a12-0974d7bca9eb",
        "pageKey": "inbound_order_create_page",
        "pageKind": "FORM_PAGE",
        "pageName": "新建入库",
        "description": "录入供应商、仓库、经办人等基本信息，并添加入库商品明细，保存或提交入库单。",
        "componentTree": [
          {
            "tabs": [],
            "type": "PAGE_HEADER",
            "props": {
              "text": "新建入库",
              "title": "新建入库",
              "visualRole": "enterprisePageHeader",
              "rowActionIds": [],
              "elementPicker": {
                "textRoles": [
                  "title",
                  "description"
                ],
                "selectable": true
              },
              "elementStyles": {
                "text:title": {}
              },
              "a9VisualQuality": "demoGrade"
            },
            "title": "新建入库",
            "fields": [],
            "columns": [],
            "modalId": null,
            "children": [],
            "actionIds": [
              "inbound_order_create_page_cancel"
            ],
            "featureIds": [],
            "componentId": "inbound_order_create_page_header",
            "description": "录入供应商、仓库、经办人等基本信息，并添加入库商品明细，保存或提交入库单。",
            "dataBindingKey": null
          },
          {
            "tabs": [],
            "type": "TOOLBAR",
            "props": {
              "placement": "pageHeader",
              "visualRole": "enterpriseTopToolbar",
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
              "inbound_order_create_page_save_draft",
              "inbound_order_create_page_submit",
              "inbound_order_create_page_cancel"
            ],
            "featureIds": [],
            "componentId": "inbound_order_create_page_page_actions",
            "description": "",
            "dataBindingKey": null
          },
          {
            "tabs": [],
            "type": "SECTION",
            "props": {
              "layout": {
                "columns": 1
              },
              "visualRole": "enterpriseFormSection",
              "rowActionIds": [],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "基本信息",
            "fields": [],
            "columns": [],
            "modalId": null,
            "children": [
              {
                "tabs": [],
                "type": "FORM",
                "props": {
                  "layout": {
                    "gap": "18px 24px",
                    "columns": 4
                  },
                  "visualRole": "enterpriseMainForm",
                  "rowActionIds": [],
                  "elementPicker": {
                    "selectable": true
                  },
                  "elementStyles": {},
                  "a9VisualQuality": "demoGrade"
                },
                "title": "基本信息",
                "fields": [
                  {
                    "type": "INPUT",
                    "label": "入库单号",
                    "options": [],
                    "disabled": false,
                    "fieldKey": "order_no",
                    "helpText": "保存后不可修改",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "系统自动生成，可手动修改",
                    "defaultValue": null,
                    "validationRuleIds": [
                      "order_no_required",
                      "order_no_max_length"
                    ]
                  },
                  {
                    "type": "SELECT",
                    "label": "供应商",
                    "options": [
                      {
                        "label": "上海华宇供应链有限公司",
                        "value": "SUP001",
                        "disabled": false
                      },
                      {
                        "label": "北京恒达贸易有限公司",
                        "value": "SUP002",
                        "disabled": false
                      },
                      {
                        "label": "广州联创实业有限公司",
                        "value": "SUP003",
                        "disabled": false
                      }
                    ],
                    "disabled": false,
                    "fieldKey": "supplier",
                    "helpText": "该项为必填，请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请选择供应商",
                    "defaultValue": null,
                    "validationRuleIds": [
                      "supplier_required"
                    ]
                  },
                  {
                    "type": "SELECT",
                    "label": "仓库",
                    "options": [
                      {
                        "label": "华东一号仓",
                        "value": "WH001",
                        "disabled": false
                      },
                      {
                        "label": "华北二号仓",
                        "value": "WH002",
                        "disabled": false
                      },
                      {
                        "label": "华南三号仓",
                        "value": "WH003",
                        "disabled": false
                      }
                    ],
                    "disabled": false,
                    "fieldKey": "warehouse",
                    "helpText": "该项为必填，请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请选择仓库",
                    "defaultValue": null,
                    "validationRuleIds": [
                      "warehouse_required"
                    ]
                  },
                  {
                    "type": "SELECT",
                    "label": "经办人",
                    "options": [
                      {
                        "label": "张伟",
                        "value": "U001",
                        "disabled": false
                      },
                      {
                        "label": "李娜",
                        "value": "U002",
                        "disabled": false
                      },
                      {
                        "label": "王强",
                        "value": "U003",
                        "disabled": false
                      }
                    ],
                    "disabled": false,
                    "fieldKey": "handler",
                    "helpText": "该项为必填，请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请选择经办人",
                    "defaultValue": null,
                    "validationRuleIds": [
                      "handler_required"
                    ]
                  },
                  {
                    "type": "DATE",
                    "label": "预计到货日期",
                    "options": [],
                    "disabled": false,
                    "fieldKey": "expected_date",
                    "helpText": "",
                    "required": false,
                    "featureIds": [],
                    "placeholder": "请选择预计到货日期",
                    "defaultValue": null,
                    "validationRuleIds": []
                  },
                  {
                    "type": "TEXTAREA",
                    "label": "备注",
                    "options": [],
                    "disabled": false,
                    "fieldKey": "remark",
                    "helpText": "最多200字",
                    "required": false,
                    "featureIds": [],
                    "placeholder": "请输入备注信息",
                    "defaultValue": null,
                    "validationRuleIds": []
                  },
                  {
                    "type": "INPUT",
                    "label": "入库单号",
                    "options": [],
                    "disabled": false,
                    "fieldKey": "inboundno",
                    "helpText": "必填项请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请输入入库单号",
                    "defaultValue": null,
                    "validationRuleIds": []
                  },
                  {
                    "type": "SELECT",
                    "label": "供应商",
                    "options": [
                      {
                        "label": "上海中转供应商",
                        "value": "supplier_sh",
                        "disabled": false
                      },
                      {
                        "label": "华南原材料供应商",
                        "value": "supplier_hn",
                        "disabled": false
                      }
                    ],
                    "disabled": false,
                    "fieldKey": "supplierid",
                    "helpText": "必填项请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请选择供应商",
                    "defaultValue": null,
                    "validationRuleIds": []
                  },
                  {
                    "type": "SELECT",
                    "label": "仓库",
                    "options": [
                      {
                        "label": "一号主仓",
                        "value": "wh_main",
                        "disabled": false
                      },
                      {
                        "label": "原材料仓",
                        "value": "wh_material",
                        "disabled": false
                      }
                    ],
                    "disabled": false,
                    "fieldKey": "warehouseid",
                    "helpText": "必填项请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请选择仓库",
                    "defaultValue": null,
                    "validationRuleIds": []
                  },
                  {
                    "type": "DATE",
                    "label": "入库日期",
                    "options": [],
                    "disabled": false,
                    "fieldKey": "inbounddate",
                    "helpText": "必填项请按实际业务信息填写。",
                    "required": true,
                    "featureIds": [],
                    "placeholder": "请输入入库日期",
                    "defaultValue": null,
                    "validationRuleIds": []
                  }
                ],
                "columns": [],
                "modalId": null,
                "children": [],
                "actionIds": [
                  "inbound_order_create_page_basic_form_submit",
                  "inbound_order_create_page_basic_form_reset"
                ],
                "featureIds": [],
                "componentId": "inbound_order_create_page_basic_form",
                "description": "填写业务单据的主体信息。",
                "dataBindingKey": null
              }
            ],
            "actionIds": [],
            "featureIds": [],
            "componentId": "inbound_order_create_page_basic_section",
            "description": "主表单据信息，采用企业后台四列表单布局。",
            "dataBindingKey": null
          },
          {
            "tabs": [],
            "type": "SECTION",
            "props": {
              "layout": {
                "columns": 1
              },
              "visualRole": "enterpriseDetailSection",
              "rowActionIds": [],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "明细信息",
            "fields": [],
            "columns": [],
            "modalId": null,
            "children": [
              {
                "tabs": [],
                "type": "TABLE",
                "props": {
                  "density": "compact",
                  "editable": true,
                  "visualRole": "enterpriseEditableTable",
                  "rowActionIds": [
                    "inbound_order_create_page_remove_item"
                  ],
                  "elementPicker": {
                    "selectable": true
                  },
                  "elementStyles": {},
                  "a9VisualQuality": "demoGrade"
                },
                "title": "商品明细",
                "fields": [],
                "columns": [
                  {
                    "type": "TEXT",
                    "label": "商品编码",
                    "width": 120,
                    "fieldKey": "product_code",
                    "sortable": false,
                    "columnKey": "product_code"
                  },
                  {
                    "type": "TEXT",
                    "label": "商品名称",
                    "width": 200,
                    "fieldKey": "product_name",
                    "sortable": false,
                    "columnKey": "product_name"
                  },
                  {
                    "type": "TEXT",
                    "label": "规格型号",
                    "width": 150,
                    "fieldKey": "specification",
                    "sortable": false,
                    "columnKey": "specification"
                  },
                  {
                    "type": "TEXT",
                    "label": "单位",
                    "width": 80,
                    "fieldKey": "unit",
                    "sortable": false,
                    "columnKey": "unit"
                  },
                  {
                    "type": "NUMBER",
                    "label": "入库数量",
                    "width": 120,
                    "fieldKey": "quantity",
                    "sortable": false,
                    "columnKey": "quantity"
                  },
                  {
                    "type": "NUMBER",
                    "label": "单价",
                    "width": 120,
                    "fieldKey": "unit_price",
                    "sortable": false,
                    "columnKey": "unit_price"
                  },
                  {
                    "type": "NUMBER",
                    "label": "金额",
                    "width": 120,
                    "fieldKey": "amount",
                    "sortable": false,
                    "columnKey": "amount"
                  },
                  {
                    "type": "ACTIONS",
                    "label": "操作",
                    "width": 100,
                    "fieldKey": "actions",
                    "sortable": false,
                    "columnKey": "actions"
                  },
                  {
                    "type": "TEXT",
                    "label": "商品编码",
                    "width": 150,
                    "fieldKey": "productCode",
                    "sortable": true,
                    "columnKey": "productcode"
                  }
                ],
                "modalId": null,
                "children": [],
                "actionIds": [
                  "inbound_order_create_page_add_item"
                ],
                "featureIds": [],
                "componentId": "inbound_order_create_page_detail_table",
                "description": "维护本次单据关联的商品行。",
                "dataBindingKey": "inboundItems"
              }
            ],
            "actionIds": [],
            "featureIds": [],
            "componentId": "inbound_order_create_page_detail_section",
            "description": "明细表格占满内容区，支持新增和行级操作。",
            "dataBindingKey": null
          },
          {
            "tabs": [],
            "type": "ALERT",
            "props": {
              "level": "info",
              "message": "请确认基本信息和明细商品后再提交。",
              "visualRole": "enterpriseValidationHint",
              "rowActionIds": [],
              "elementPicker": {
                "selectable": true
              },
              "elementStyles": {},
              "a9VisualQuality": "demoGrade"
            },
            "title": "业务校验",
            "fields": [],
            "columns": [],
            "modalId": null,
            "children": [],
            "actionIds": [],
            "featureIds": [],
            "componentId": "inbound_order_create_page_business_hint",
            "description": "提交前将检查必填项、明细行数和数量合法性。",
            "dataBindingKey": null
          },
          {
            "tabs": [],
            "type": "TOOLBAR",
            "props": {
              "placement": "bottom",
              "visualRole": "enterpriseBottomToolbar",
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
              "inbound_order_create_page_save_draft",
              "inbound_order_create_page_submit",
              "inbound_order_create_page_cancel"
            ],
            "featureIds": [],
            "componentId": "inbound_order_create_page_bottom_actions",
            "description": "",
            "dataBindingKey": null
          }
        ]
      },
      "pages": [],
      "module": {},
      "routes": [],
      "identity": {
        "title": "新建入库",
        "pageId": "51d24519-5f5c-4518-8a12-0974d7bca9eb",
        "pageKey": "inbound_order_create_page",
        "pageKind": "FORM_PAGE",
        "pageName": "新建入库",
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
          "pageName": "入库单创建页",
          "generatedFor": "UI_CONTRACT_MOCK_PREVIEW"
        },
        "basic_info": {
          "remark": "加急订单，请尽快处理",
          "handler": "U001",
          "order_no": "RK20260803001",
          "supplier": "SUP001",
          "warehouse": "WH001",
          "expected_date": "2026-08-05"
        },
        "detail_items": [
          {
            "unit": "个",
            "amount": 5900,
            "quantity": 100,
            "unit_price": 59,
            "product_code": "P10001",
            "product_name": "无线鼠标",
            "specification": "黑色 2.4G"
          },
          {
            "unit": "个",
            "amount": 14950,
            "quantity": 50,
            "unit_price": 299,
            "product_code": "P10002",
            "product_name": "机械键盘",
            "specification": "青轴 104键"
          }
        ],
        "inboundItems": [
          {
            "unit": "箱",
            "amount": "示例",
            "actions": "示例",
            "quantity": 120,
            "unit_price": "示例",
            "productCode": "P-10001",
            "product_code": "示例",
            "product_name": "示例",
            "specification": "示例"
          }
        ],
        "featureCoveragePreview": [
          {
            "status": "已覆盖",
            "featureId": "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f",
            "featureName": "入库单创建"
          }
        ]
      },
      "pageStates": [
        {
          "type": "DEFAULT",
          "title": "默认状态",
          "message": "表单可编辑，明细表格为空",
          "stateKey": "DEFAULT",
          "visibleComponentIds": [],
          "disabledComponentIds": []
        },
        {
          "type": "LOADING",
          "title": "加载中",
          "message": "正在加载基础数据...",
          "stateKey": "LOADING",
          "visibleComponentIds": [],
          "disabledComponentIds": []
        },
        {
          "type": "ERROR",
          "title": "加载失败",
          "message": "基础数据加载失败，请稍后重试",
          "stateKey": "ERROR",
          "visibleComponentIds": [],
          "disabledComponentIds": []
        },
        {
          "type": "EMPTY",
          "title": "暂无明细",
          "message": "请点击“添加商品”按钮添加入库明细",
          "stateKey": "EMPTY",
          "visibleComponentIds": [],
          "disabledComponentIds": []
        },
        {
          "type": "SUCCESS",
          "title": "操作成功",
          "message": "关键操作完成后展示成功反馈。",
          "stateKey": "SUCCESS",
          "visibleComponentIds": [],
          "disabledComponentIds": []
        },
        {
          "type": "DEFAULT",
          "title": "保存中",
          "message": "正在保存入库单...",
          "stateKey": "SAVING",
          "visibleComponentIds": [],
          "disabledComponentIds": []
        }
      ],
      "apiBindings": [
        {
          "path": "/api/inbound-orders/draft",
          "method": "POST",
          "status": "MOCK",
          "bindingKey": "inbound_order_save",
          "requestSchema": {
            "type": "object",
            "required": [
              "orderNo",
              "supplier",
              "warehouse",
              "handler"
            ],
            "properties": {
              "items": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "quantity": {
                      "type": "integer"
                    },
                    "unitPrice": {
                      "type": "number"
                    },
                    "productCode": {
                      "type": "string"
                    }
                  }
                }
              },
              "remark": {
                "type": "string"
              },
              "handler": {
                "type": "string"
              },
              "orderNo": {
                "type": "string"
              },
              "supplier": {
                "type": "string"
              },
              "warehouse": {
                "type": "string"
              },
              "expectedDate": {
                "type": "string",
                "format": "date"
              }
            }
          },
          "responseSchema": {
            "type": "object",
            "properties": {
              "status": {
                "type": "string"
              },
              "orderId": {
                "type": "string"
              }
            }
          },
          "usedByActionIds": [
            "save_draft"
          ]
        },
        {
          "path": "/api/inbound-orders",
          "method": "POST",
          "status": "MOCK",
          "bindingKey": "inbound_order_submit",
          "requestSchema": {
            "type": "object",
            "required": [
              "orderNo",
              "supplier",
              "warehouse",
              "handler"
            ],
            "properties": {
              "items": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "quantity": {
                      "type": "integer"
                    },
                    "unitPrice": {
                      "type": "number"
                    },
                    "productCode": {
                      "type": "string"
                    }
                  }
                }
              },
              "remark": {
                "type": "string"
              },
              "handler": {
                "type": "string"
              },
              "orderNo": {
                "type": "string"
              },
              "supplier": {
                "type": "string"
              },
              "warehouse": {
                "type": "string"
              },
              "expectedDate": {
                "type": "string",
                "format": "date"
              }
            }
          },
          "responseSchema": {
            "type": "object",
            "properties": {
              "status": {
                "type": "string"
              },
              "orderId": {
                "type": "string"
              }
            }
          },
          "usedByActionIds": [
            "submit_order"
          ]
        },
        {
          "path": "/api/inbound_order_create_page/inbound-order-create",
          "method": "POST",
          "status": "PLANNED",
          "bindingKey": "inbound_order_create_action",
          "requestSchema": {},
          "responseSchema": {},
          "usedByActionIds": [
            "inbound_order_create_action"
          ]
        }
      ],
      "assumptions": [
        "供应商、仓库、经办人选项数据由后端接口提供，当前使用 Mock 数据。",
        "入库单号默认由系统生成，允许用户手动修改。",
        "明细表格支持添加和删除商品行，具体商品选择通过弹窗实现。",
        "保存草稿和提交入库单分别调用不同接口。",
        "A9.7: generated prototype is hardened as a demo-grade, canvas-selectable, AI-editable UI contract."
      ],
      "artifactType": "交互原型",
      "interactions": [
        {
          "type": "NAVIGATE",
          "label": "返回列表",
          "payload": {},
          "actionId": "back_to_list",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": "a44d763d-16e3-429f-9e14-870c2b8e496d",
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "保存草稿",
          "payload": {},
          "actionId": "save_draft",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": "save_draft_success",
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": "inbound_order_save",
          "failureActionIds": [
            "show_save_error"
          ],
          "successActionIds": [
            "show_save_success"
          ],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "提交入库单",
          "payload": {},
          "actionId": "submit_order",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": "submit_order_success",
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": "inbound_order_submit",
          "failureActionIds": [
            "show_submit_error"
          ],
          "successActionIds": [
            "show_submit_success"
          ],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "NAVIGATE",
          "label": "取消",
          "payload": {},
          "actionId": "cancel_create",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": "a44d763d-16e3-429f-9e14-870c2b8e496d",
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SHOW_TOAST",
          "label": "添加商品",
          "payload": {},
          "actionId": "add_item",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "DELETE_ROW",
          "label": "删除",
          "payload": {},
          "actionId": "delete_item",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": "确定删除该商品明细吗？"
        },
        {
          "type": "SHOW_TOAST",
          "label": "保存成功",
          "payload": {
            "type": "success",
            "message": "入库单草稿保存成功"
          },
          "actionId": "show_save_success",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SHOW_TOAST",
          "label": "保存失败",
          "payload": {
            "type": "error",
            "message": "保存失败，请检查必填项"
          },
          "actionId": "show_save_error",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SHOW_TOAST",
          "label": "提交成功",
          "payload": {
            "type": "success",
            "message": "入库单提交成功，等待审核"
          },
          "actionId": "show_submit_success",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SHOW_TOAST",
          "label": "提交失败",
          "payload": {
            "type": "error",
            "message": "提交失败，请检查必填项"
          },
          "actionId": "show_submit_error",
          "targetId": "inbound_order_create_page_page_actions",
          "featureIds": [
            "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f"
          ],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "提交",
          "payload": {
            "source": "A9_7_quality_hardener"
          },
          "actionId": "basic_info_form_submit",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "RESET_FORM",
          "label": "重置",
          "payload": {
            "source": "A9_7_quality_hardener"
          },
          "actionId": "basic_info_form_reset",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "保存草稿",
          "payload": {
            "source": "A9_7_REAL_GENERATION_HARDENER"
          },
          "actionId": "inbound_order_create_page_save_draft",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "提交",
          "payload": {
            "source": "A9_7_REAL_GENERATION_HARDENER"
          },
          "actionId": "inbound_order_create_page_submit",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "RESET_FORM",
          "label": "取消",
          "payload": {
            "source": "A9_7_REAL_GENERATION_HARDENER"
          },
          "actionId": "inbound_order_create_page_cancel",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "SHOW_TOAST",
          "label": "添加商品",
          "payload": {
            "source": "A9_7_REAL_GENERATION_HARDENER"
          },
          "actionId": "inbound_order_create_page_add_item",
          "targetId": "inbound_order_create_page_detail_table",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_detail_table",
          "confirmationMessage": ""
        },
        {
          "type": "DELETE_ROW",
          "label": "删除",
          "payload": {
            "source": "A9_7_REAL_GENERATION_HARDENER"
          },
          "actionId": "inbound_order_create_page_remove_item",
          "targetId": "inbound_order_create_page_detail_table",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_detail_table",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "入库单创建",
          "payload": {},
          "actionId": "inbound_order_create_action",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": "inbound_order_create_action",
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_page_actions",
          "confirmationMessage": ""
        },
        {
          "type": "SUBMIT_FORM",
          "label": "提交",
          "payload": {
            "source": "A9_7_quality_hardener"
          },
          "actionId": "inbound_order_create_page_basic_form_submit",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        },
        {
          "type": "RESET_FORM",
          "label": "重置",
          "payload": {
            "source": "A9_7_quality_hardener"
          },
          "actionId": "inbound_order_create_page_basic_form_reset",
          "targetId": "inbound_order_create_page_basic_form",
          "featureIds": [],
          "scenarioKey": null,
          "targetPageId": null,
          "targetTabKey": null,
          "dataBindingKey": null,
          "failureActionIds": [],
          "successActionIds": [],
          "sourceComponentId": "inbound_order_create_page_basic_form",
          "confirmationMessage": ""
        }
      ],
      "mockScenarios": [
        {
          "delayMs": 500,
          "message": "入库单草稿保存成功",
          "outcome": "SUCCESS",
          "actionId": "save_draft",
          "response": {
            "status": "DRAFT",
            "orderId": "RK20260803001"
          },
          "scenarioKey": "save_draft_success",
          "nextPageState": "DEFAULT"
        },
        {
          "delayMs": 500,
          "message": "保存失败，请检查必填项",
          "outcome": "FAILURE",
          "actionId": "save_draft",
          "response": {
            "message": "请检查必填项",
            "errorCode": "VALIDATION_ERROR"
          },
          "scenarioKey": "save_draft_failure",
          "nextPageState": "ERROR"
        },
        {
          "delayMs": 800,
          "message": "入库单提交成功，等待审核",
          "outcome": "SUCCESS",
          "actionId": "submit_order",
          "response": {
            "status": "PENDING_APPROVAL",
            "orderId": "RK20260803001"
          },
          "scenarioKey": "submit_order_success",
          "nextPageState": "SUCCESS"
        },
        {
          "delayMs": 800,
          "message": "提交失败，请检查必填项",
          "outcome": "FAILURE",
          "actionId": "submit_order",
          "response": {
            "message": "请检查必填项",
            "errorCode": "VALIDATION_ERROR"
          },
          "scenarioKey": "submit_order_failure",
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
      "analysisSummary": "本页面为入库单创建页，采用表单+明细表格布局。包含基本信息表单（供应商、仓库、经办人等）和入库商品明细表格，支持保存草稿和提交入库单操作。页面状态覆盖默认、加载、错误、空和保存中。所有字段、按钮、表格列均定义了稳定的 key，便于画布选择和后续开发。 已补强 featureCoverage、pageStates、mockScenarios、apiBindings、mockData、developmentRules 和 Development Agent 交接摘要；当前 FEATURE 覆盖率 100%，功能覆盖项 1 个。",
      "featureCoverage": [
        {
          "notes": [
            "已由 UI_CONTRACT 最终引用守门器归一化为当前 PAGE 真实引用。"
          ],
          "status": "FULL",
          "actionIds": [
            "back_to_list",
            "save_draft",
            "submit_order",
            "cancel_create",
            "add_item",
            "delete_item",
            "show_save_success",
            "show_save_error",
            "show_submit_success",
            "show_submit_error"
          ],
          "featureId": "a1f61fc8-ef53-41a7-8e74-98dd5fdd403f",
          "featureKey": "inbound_order_create",
          "featureName": "入库单创建",
          "componentIds": [
            "inbound_order_create_page_page_actions"
          ]
        }
      ],
      "validationRules": [
        {
          "type": "REQUIRED",
          "value": null,
          "ruleId": "order_no_required",
          "message": "请输入入库单号",
          "fieldKey": "order_no",
          "compareFieldKey": null
        },
        {
          "type": "MAX_LENGTH",
          "value": 50,
          "ruleId": "order_no_max_length",
          "message": "入库单号不能超过50个字符",
          "fieldKey": "order_no",
          "compareFieldKey": null
        },
        {
          "type": "REQUIRED",
          "value": null,
          "ruleId": "supplier_required",
          "message": "请选择供应商",
          "fieldKey": "supplier",
          "compareFieldKey": null
        },
        {
          "type": "REQUIRED",
          "value": null,
          "ruleId": "warehouse_required",
          "message": "请选择仓库",
          "fieldKey": "warehouse",
          "compareFieldKey": null
        },
        {
          "type": "REQUIRED",
          "value": null,
          "ruleId": "handler_required",
          "message": "请选择经办人",
          "fieldKey": "handler",
          "compareFieldKey": null
        }
      ],
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
          "route": "仓储管理域/入库管理/入库单管理/入库单查询/入库单列表页",
          "status": "RESOLVED",
          "targetPageId": "a44d763d-16e3-429f-9e14-870c2b8e496d",
          "targetPageKey": "inbound_order_list_page",
          "targetPageName": "入库单列表页"
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
        },
        {
          "route": "仓储管理域/入库管理/入库单管理/入库单明细查看/入库单详情页",
          "status": "RESOLVED",
          "targetPageId": "d2dfd21d-b616-4c48-8464-5865a65b57ce",
          "targetPageKey": "inbound_order_detail_page",
          "targetPageName": "入库单详情页"
        }
      ]
    },
    "uiContractSource": "MODULE_PROTOTYPE_SNAPSHOT",
    "placeholderOnly": false,
    "fields": [
      {
        "key": "id",
        "label": "ID",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "order_no",
        "label": "入库单号",
        "type": "TEXT",
        "required": true
      },
      {
        "key": "supplier",
        "label": "供应商",
        "type": "SELECT",
        "required": true
      },
      {
        "key": "warehouse",
        "label": "仓库",
        "type": "SELECT",
        "required": true
      },
      {
        "key": "handler",
        "label": "经办人",
        "type": "SELECT",
        "required": true
      },
      {
        "key": "expected_date",
        "label": "预计到货日期",
        "type": "DATE",
        "required": false
      },
      {
        "key": "remark",
        "label": "备注",
        "type": "TEXTAREA",
        "required": false
      },
      {
        "key": "inboundno",
        "label": "入库单号",
        "type": "TEXT",
        "required": true
      },
      {
        "key": "supplierid",
        "label": "供应商",
        "type": "SELECT",
        "required": true
      },
      {
        "key": "warehouseid",
        "label": "仓库",
        "type": "SELECT",
        "required": true
      },
      {
        "key": "inbounddate",
        "label": "入库日期",
        "type": "DATE",
        "required": true
      },
      {
        "key": "product_code",
        "label": "商品编码",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "product_name",
        "label": "商品名称",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "specification",
        "label": "规格型号",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "unit",
        "label": "单位",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "quantity",
        "label": "入库数量",
        "type": "NUMBER",
        "required": false
      },
      {
        "key": "unit_price",
        "label": "单价",
        "type": "NUMBER",
        "required": false
      },
      {
        "key": "amount",
        "label": "金额",
        "type": "NUMBER",
        "required": false
      }
    ],
    "seedRecords": [
      {
        "id": "inbound_order_create_page-001",
        "order_no": "入库单号样例 1",
        "supplier": "供应商样例 1",
        "warehouse": "仓库样例 1",
        "handler": "经办人样例 1",
        "expected_date": "2026-08-11",
        "remark": "入库单创建页样例说明 1",
        "inboundno": "入库单号样例 1",
        "supplierid": "供应商样例 1",
        "warehouseid": "仓库样例 1",
        "inbounddate": "2026-08-11",
        "product_code": "商品编码样例 1",
        "product_name": "商品名称样例 1",
        "specification": "规格型号样例 1",
        "unit": "单位样例 1",
        "quantity": 10,
        "unit_price": 10,
        "amount": 10,
        "createdAt": "2026-08-11 09:30:00"
      },
      {
        "id": "inbound_order_create_page-002",
        "order_no": "入库单号样例 2",
        "supplier": "供应商样例 2",
        "warehouse": "仓库样例 2",
        "handler": "经办人样例 2",
        "expected_date": "2026-08-12",
        "remark": "入库单创建页样例说明 2",
        "inboundno": "入库单号样例 2",
        "supplierid": "供应商样例 2",
        "warehouseid": "仓库样例 2",
        "inbounddate": "2026-08-12",
        "product_code": "商品编码样例 2",
        "product_name": "商品名称样例 2",
        "specification": "规格型号样例 2",
        "unit": "单位样例 2",
        "quantity": 20,
        "unit_price": 20,
        "amount": 20,
        "createdAt": "2026-08-12 09:30:00"
      },
      {
        "id": "inbound_order_create_page-003",
        "order_no": "入库单号样例 3",
        "supplier": "供应商样例 3",
        "warehouse": "仓库样例 3",
        "handler": "经办人样例 3",
        "expected_date": "2026-08-13",
        "remark": "入库单创建页样例说明 3",
        "inboundno": "入库单号样例 3",
        "supplierid": "供应商样例 3",
        "warehouseid": "仓库样例 3",
        "inbounddate": "2026-08-13",
        "product_code": "商品编码样例 3",
        "product_name": "商品名称样例 3",
        "specification": "规格型号样例 3",
        "unit": "单位样例 3",
        "quantity": 30,
        "unit_price": 30,
        "amount": 30,
        "createdAt": "2026-08-13 09:30:00"
      }
    ]
  },
  {
    "resourceKey": "inbound_order_list_page",
    "apiSegment": "inbound-orders-2",
    "title": "入库单列表页",
    "pageKey": "inbound_order_list_page",
    "routePath": "/inbound/orders",
    "description": "面向业务人员的入库单列表页查询、筛选和数据管理页面。",
    "pageKind": "list_page",
    "surfaceType": "MAIN_NAV_PAGE",
    "navigationVisible": true,
    "entryMode": "NAVIGATION",
    "uiContract": {
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
    },
    "uiContractSource": "MODULE_PROTOTYPE_SNAPSHOT",
    "placeholderOnly": false,
    "fields": [
      {
        "key": "id",
        "label": "ID",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "inboundno",
        "label": "入库单号",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "supplierid",
        "label": "供应商",
        "type": "SELECT",
        "required": false
      },
      {
        "key": "warehouseid",
        "label": "仓库",
        "type": "SELECT",
        "required": false
      },
      {
        "key": "keyword",
        "label": "关键字",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "status",
        "label": "入库状态",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "operatorid",
        "label": "经办人",
        "type": "TEXT",
        "required": false
      },
      {
        "key": "createdat",
        "label": "创建时间",
        "type": "DATE",
        "required": false
      },
      {
        "key": "inboundqty",
        "label": "入库数量",
        "type": "NUMBER",
        "required": false
      }
    ],
    "seedRecords": [
      {
        "id": "inbound_order_list_page-001",
        "inboundno": "入库单号样例 1",
        "supplierid": "供应商样例 1",
        "warehouseid": "仓库样例 1",
        "keyword": "关键字样例 1",
        "status": "待处理",
        "operatorid": "经办人样例 1",
        "createdat": "2026-08-11 09:30:00",
        "inboundqty": 10,
        "createdAt": "2026-08-11 09:30:00"
      },
      {
        "id": "inbound_order_list_page-002",
        "inboundno": "入库单号样例 2",
        "supplierid": "供应商样例 2",
        "warehouseid": "仓库样例 2",
        "keyword": "关键字样例 2",
        "status": "处理中",
        "operatorid": "经办人样例 2",
        "createdat": "2026-08-12 09:30:00",
        "inboundqty": 20,
        "createdAt": "2026-08-12 09:30:00"
      },
      {
        "id": "inbound_order_list_page-003",
        "inboundno": "入库单号样例 3",
        "supplierid": "供应商样例 3",
        "warehouseid": "仓库样例 3",
        "keyword": "关键字样例 3",
        "status": "已完成",
        "operatorid": "经办人样例 3",
        "createdat": "2026-08-13 09:30:00",
        "inboundqty": 30,
        "createdAt": "2026-08-13 09:30:00"
      }
    ]
  },
  {
    "resourceKey": "inbound_order_detail_page",
    "apiSegment": "inbound-orders-3",
    "title": "入库单详情页",
    "pageKey": "inbound_order_detail_page",
    "routePath": "/inbound/orders/:id",
    "description": "该页面已纳入模块开发基线，但当前未提供可生成的 PAGE_UI_CONTRACT。",
    "pageKind": "detail_page",
    "surfaceType": "SECONDARY_ROUTE",
    "navigationVisible": false,
    "entryMode": "ACTION_ROUTE",
    "uiContract": {},
    "uiContractSource": "MODULE_PROTOTYPE_PLACEHOLDER",
    "placeholderOnly": true,
    "fields": [],
    "seedRecords": []
  },
  {
    "resourceKey": "inbound_order_quantity_edit_page",
    "apiSegment": "inbound-orders-4",
    "title": "入库单数量编辑页",
    "pageKey": "inbound_order_quantity_edit_page",
    "routePath": "/inbound/orders/:id/edit-quantity",
    "description": "该页面已纳入模块开发基线，但当前未提供可生成的 PAGE_UI_CONTRACT。",
    "pageKind": "form_page",
    "surfaceType": "SECONDARY_ROUTE",
    "navigationVisible": false,
    "entryMode": "ACTION_ROUTE",
    "uiContract": {},
    "uiContractSource": "MODULE_PROTOTYPE_PLACEHOLDER",
    "placeholderOnly": true,
    "fields": [],
    "seedRecords": []
  },
  {
    "resourceKey": "inbound_order_approval_page",
    "apiSegment": "inbound-orders-5",
    "title": "入库单审核页",
    "pageKey": "inbound_order_approval_page",
    "routePath": "/inbound/orders/approval",
    "description": "该页面已纳入模块开发基线，但当前未提供可生成的 PAGE_UI_CONTRACT。",
    "pageKind": "approval_page",
    "surfaceType": "SECONDARY_ROUTE",
    "navigationVisible": false,
    "entryMode": "ACTION_ROUTE",
    "uiContract": {},
    "uiContractSource": "MODULE_PROTOTYPE_PLACEHOLDER",
    "placeholderOnly": true,
    "fields": [],
    "seedRecords": []
  }
]
