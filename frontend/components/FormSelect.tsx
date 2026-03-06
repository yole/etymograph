import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";

interface FormSelectOption {
    id: string;
    text: string;
}

interface FormSelectProps extends FormFieldProps {
    selection: any;
    options: FormSelectOption[];
}

export default function FormSelect(props: FormSelectProps) {
    const form = useEtymographFormContext()
    const formValues = form.getValues()
    const value = formValues[props.id] ?? props.selection ?? "-1"

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <select id={props.id} value={value} onChange={(event) => form.setFieldValue(props.id, event.currentTarget.value)}>
                <option value="-1">-</option>
                {props.options.map(mc => <option key={mc.id} value={mc.id}>{mc.text}</option>)}
            </select>
        </td>
    </tr>
}
