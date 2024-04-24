import {Controller, useFormContext} from "react-hook-form";
import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import Select from "react-select";

export default function RuleListSelect(props) {
    const {control} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const rules = globalState.rules.map((r) => ({
        value: r.name,
        label: r.summaryText === null || r.summaryText === "" || r.summaryText.length > 15 ? r.name :`${r.name} (${r.summaryText})`})
    )

    const valueFn = props.isMulti
        ? (value) => value === undefined ? [] : value.split(",").map(s => rules.find(r => r.value === s))
        : (value) => rules.find((r) => r.value === value)

    function changeFn(onChange) {
        return props.isMulti
            ? (val) => onChange(val.map(c => c.value).join(","))
            : (val) => onChange(val.value)
    }

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Controller
                render={({ field: { onChange, value, name, ref } }) =>
                    <Select options={rules}
                            isMulti={props.isMulti}
                            value={valueFn(value)}
                            onChange={changeFn(onChange)}
                    /> }
                name={props.id}
                control={control}
            />
        </td>
    </tr>
}
