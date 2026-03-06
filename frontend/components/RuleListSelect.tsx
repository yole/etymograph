import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";

interface RuleLisSelectProps extends FormFieldProps {
    languageProp?: string;
    language?: string;
    showNone?: boolean;
    isMulti?: boolean;
}

export default function RuleListSelect(props: RuleLisSelectProps) {
    const form = useEtymographFormContext()
    const formValues = form.getValues()
    const globalState = useContext(GlobalStateContext)
    const lang = props.languageProp !== undefined
        ? formValues[props.languageProp]
        : props.language
    let rules = globalState.rules.filter(r => !lang || lang === r.toLang).map((r) => ({
        value: r.name,
        label: r.summaryText === null || r.summaryText === "" || r.summaryText.length > 15 ? r.name :`${r.name} (${r.summaryText})`})
    )
    if (props.showNone) {
        rules = [{value: '', label: 'None'}].concat(rules)
    }

    const value = formValues[props.id]
    const selectedValue = props.isMulti
        ? (value === undefined ? [] : value.split(",").map(s => rules.find(r => r.value === s)).filter(Boolean))
        : rules.find((r) => r.value === value) ?? null

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Select options={rules}
                    isMulti={props.isMulti}
                    value={selectedValue}
                    onChange={(val) => props.isMulti
                        ? form.setFieldValue(props.id, (val ?? []).map(c => c.value).join(","))
                        : form.setFieldValue(props.id, val?.value ?? "")}
            />
        </td>
    </tr>
}
