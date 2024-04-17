import {Controller, useFormContext} from "react-hook-form";
import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import Select from "react-select";

export default function RuleListSelect(props) {
    const {control} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const rules = globalState.rules.map((r) => ({value: r.name, label: `${r.name} (${r.summaryText})`}))

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Controller
                render={({ field: { onChange, value, name, ref } }) =>
                    <Select options={rules}
                            isMulti={true}
                            value={value === undefined ? [] : value.split(",").map(s => rules.find(r => r.value === s))}
                            onChange={(val) => onChange(val.map(c => c.value).join(","))}
                    /> }
                name={props.id}
                control={control}
            />
        </td>
    </tr>
}
